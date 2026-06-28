package com.pocketstock.ledger.calendar;

import com.pocketstock.ledger.calendar.YahooDividendClient.YahooDividend;
import com.pocketstock.ledger.client.CalendarFeignClient;
import com.pocketstock.ledger.client.dto.StockEventUpsertRequest;
import com.pocketstock.ledger.exchange.CurrencyRateProvider;
import com.pocketstock.ledger.lifecycle.LedgerActivation;
import com.pocketstock.ledger.trading.domain.TradableStock;
import com.pocketstock.ledger.trading.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 해외 배당일정 배치 — 야후 차트({@link YahooDividendClient})로 미국 배당주의 주당배당금·다음 배당락일을
 * 받아 core-api {@code stock_events}(DB A)에 upsert한다. KIS 예탁원 배치({@link DividendBatchService})가
 * 국내만 커버하는 빈자리를 메우는, 같은 테이블·같은 컨벤션(DIVIDEND_EX/DIVIDEND_PAY)의 해외판.
 *
 * <p><b>대상 심볼</b>: 만기추천 카탈로그({@code dividend_stocks}, market='US')와 동일한 미국 배당주 목록.
 * 카탈로그가 core(DB A) 소유라 ledger에서 직접 못 읽어, MVP에선 상수로 둔다(종목 수가 적어 야후 콜도 소량).
 * 종목이 늘면 core 내부 API로 목록을 받아오게 바꾼다.
 *
 * <p><b>chart 한계 반영</b>: 야후 chart는 과거 배당의 <b>배당락일</b>·금액만 줘서, 다음 배당락일은 주기
 * 투영으로 잡고 정확한 <b>지급일은 없다</b>. 그래서 가짜 지급일로 추천·DRIP을 오염시키지 않도록
 * {@code DIVIDEND_PAY}는 만들지 않고, 주당배당금을 {@code DIVIDEND_EX}에 실어 추천 카드의 "1주당 배당금"만
 * 채운다(지급일 미노출). 금액은 USD라 현재 환율로 KRW 환산해 적재(국내 배당과 동일하게 amount=원화).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasDividendBatchService {

    private static final String EVENT_TYPE_EX  = "DIVIDEND_EX";
    private static final String CURRENCY_USD   = "USD";

    /** 만기추천 해외 배당주 카탈로그(dividend_stocks market='US')와 동일. */
    private static final List<String> US_DIVIDEND_SYMBOLS = List.of(
            "KO", "MCD", "O", "T", "XOM", "ABBV", "PG", "PM", "VZ", "PEP");

    private final YahooDividendClient yahooDividendClient;
    private final CalendarFeignClient calendarFeignClient;
    private final CurrencyRateProvider currencyRateProvider;
    private final StockMapper stockMapper;
    private final LedgerActivation activation;

    /** 해외 배당 동기화 — 매주 월 02:30 KST(국내 배치와 별도). 부팅 시에도 1회 호출됨. */
    @Scheduled(cron = "0 30 2 * * MON", zone = "Asia/Seoul")
    public void syncOverseasDividends() {
        if (!activation.isActive()) {
            return;   // 비활성 색 — 활성 색이 배치 실행(upsert 멱등이나 단일활성 일관성).
        }

        // USD→KRW 환율 1회 확보(캐시 우선·야후 폴백). 못 받으면 환산 불가라 전체 skip.
        BigDecimal usdKrw = currencyRateProvider.current().exchangeRate();

        List<StockEventUpsertRequest> events = new ArrayList<>();
        int filled = 0;
        int failed = 0;
        for (String symbol : US_DIVIDEND_SYMBOLS) {
            YahooDividend div;
            try {
                div = yahooDividendClient.fetch(symbol);   // 실패는 예외, 이력 없음은 null
            } catch (Exception e) {
                failed++;                                   // 조회 실패 — 마지막에 예외로 올려 재시도
                log.warn("[해외배당] {} 조회 실패 — {}", symbol, e.getMessage());
                continue;
            }
            if (div == null || div.perShareAmount() == null || div.nextExDate() == null) {
                continue;   // 배당 이력 없음 — 실패 아님
            }

            String name = stockName(symbol);
            BigDecimal perShareKrw = div.perShareAmount()
                    .multiply(usdKrw)
                    .setScale(0, RoundingMode.HALF_UP);
            String detail = String.format("주당 %s %s (≈%s원), 배당락 기준일",
                    div.perShareAmount().stripTrailingZeros().toPlainString(),
                    div.currency() == null ? CURRENCY_USD : div.currency(),
                    perShareKrw.toPlainString());

            // chart는 정확한 지급일을 못 줘 DIVIDEND_PAY를 만들지 않는다(가짜 지급일이 추천·DRIP을 오염).
            // 주당배당금을 배당락(DIVIDEND_EX) 이벤트에 실어 추천 카드의 '1주당 배당금'을 채운다(지급일은 미노출).
            events.add(new StockEventUpsertRequest(
                    symbol, EVENT_TYPE_EX, div.nextExDate(), name + " 배당락", detail, perShareKrw));
            filled++;
        }

        if (!events.isEmpty()) {
            calendarFeignClient.upsertStockEvents(events);
        }
        log.info("[해외배당] 완료 — {}/{}종목 적재(실패 {}, 환율 {})",
                filled, US_DIVIDEND_SYMBOLS.size(), failed, usdKrw);

        // 적재 성공분은 위에서 이미 멱등 upsert. 조회 '실패'가 있었으면 예외로 올려 부팅 재시도
        // (BootSyncRetry)가 다시 돌게 한다 — 일시 장애가 다음 크론까지 결손으로 굳지 않도록.
        if (failed > 0) {
            throw new IllegalStateException(
                    "해외 배당 조회 실패 " + failed + "종목 — 재시도 필요");
        }
    }

    /** tradable_stocks의 한글종목명 — 없으면 심볼 그대로. */
    private String stockName(String symbol) {
        TradableStock s = stockMapper.findByCode(symbol);
        return (s != null && s.getStockName() != null) ? s.getStockName() : symbol;
    }
}
