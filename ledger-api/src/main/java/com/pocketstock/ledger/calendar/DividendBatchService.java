package com.pocketstock.ledger.calendar;

import com.pocketstock.ledger.client.CalendarFeignClient;
import com.pocketstock.ledger.client.dto.StockEventUpsertRequest;
import com.pocketstock.ledger.kis.KisDividendClient;
import com.pocketstock.ledger.kis.KisDividendResponse;
import com.pocketstock.ledger.lifecycle.LedgerActivation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * KIS 배당일정 배치 — 연 1회(1월 1일 새벽 2시) 실행.
 * 1년치 배당기준일·지급일을 조회해 core-api 내부 API로 stock_events(DB A)에 upsert한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendBatchService {

    private static final String EVENT_TYPE_EX_DIVIDEND = "DIVIDEND_EX";
    private static final String EVENT_TYPE_PAY         = "DIVIDEND_PAY";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KisDividendClient kisDividendClient;
    private final CalendarFeignClient calendarFeignClient;
    private final LedgerActivation activation;

    @Scheduled(cron = "0 0 2 1 1 *", zone = "Asia/Seoul")
    public void syncDividendEvents() {
        if (!activation.isActive()) {
            return;   // 비활성 색 — 활성 색이 배치 실행(upsert 멱등이나 단일활성 일관성).
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate oneYearLater = today.plusYears(1);

        log.info("[배당배치] 조회 기간: {} ~ {}", today, oneYearLater);

        List<KisDividendResponse.Item> items =
                kisDividendClient.fetchDividends(today, oneYearLater);

        List<StockEventUpsertRequest> events = new ArrayList<>();
        for (KisDividendResponse.Item item : items) {
            if (item.recordDate() == null || item.recordDate().isBlank()) continue;
            if (item.shtCd() == null || item.shtCd().isBlank()) continue;
            try {
                LocalDate recordDate     = parseDate(item.recordDate());
                LocalDate exDividendDate = recordDate.minusDays(1);

                String detail = String.format("현금배당금: %s원, 배당률: %s%%",
                        item.perStoDiviAmt(), item.diviRate());

                events.add(new StockEventUpsertRequest(
                        item.shtCd(), EVENT_TYPE_EX_DIVIDEND, exDividendDate,
                        item.isinName() + " 배당락", detail, null));

                if (item.diviPayDt() != null && !item.diviPayDt().isBlank()) {
                    LocalDate payDate = parseDate(item.diviPayDt());
                    events.add(new StockEventUpsertRequest(
                            item.shtCd(), EVENT_TYPE_PAY, payDate,
                            item.isinName() + " 배당금 지급", detail, parseAmount(item.perStoDiviAmt())));
                }
            } catch (DateTimeParseException e) {
                log.warn("[배당배치] 날짜 파싱 실패 — skip shtCd={} recordDate={} diviPayDt={}",
                        item.shtCd(), item.recordDate(), item.diviPayDt());
            }
        }

        if (!events.isEmpty()) {
            calendarFeignClient.upsertStockEvents(events);
        }
        log.info("[배당배치] 완료 — {}건 처리", events.size());
    }

    private static LocalDate parseDate(String raw) {
        return LocalDate.parse(raw.replace("/", ""), FMT);
    }

    /** 주당 현금배당금 파싱(콤마·공백 제거) — 없거나 숫자 아니면 null(지급 엔진이 amount 없는 행은 스킵). */
    private static java.math.BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new java.math.BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 매수 체결 이벤트 수신 시 해당 종목만 즉시 적재 (Kafka 연동 예정). */
    public void syncByStockCode(String stockCode) {
        LocalDate today        = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate oneYearLater = today.plusYears(1);

        List<KisDividendResponse.Item> items =
                kisDividendClient.fetchDividendsByStock(stockCode, today, oneYearLater);

        List<StockEventUpsertRequest> events = new ArrayList<>();
        for (KisDividendResponse.Item item : items) {
            if (item.recordDate() == null || item.recordDate().isBlank()) continue;
            if (item.shtCd() == null || item.shtCd().isBlank()) continue;
            try {
                LocalDate exDividendDate = parseDate(item.recordDate()).minusDays(1);
                String detail = String.format("현금배당금: %s원, 배당률: %s%%",
                        item.perStoDiviAmt(), item.diviRate());

                events.add(new StockEventUpsertRequest(
                        item.shtCd(), EVENT_TYPE_EX_DIVIDEND, exDividendDate,
                        item.isinName() + " 배당락", detail, null));

                if (item.diviPayDt() != null && !item.diviPayDt().isBlank()) {
                    LocalDate payDate = parseDate(item.diviPayDt());
                    events.add(new StockEventUpsertRequest(
                            item.shtCd(), EVENT_TYPE_PAY, payDate,
                            item.isinName() + " 배당금 지급", detail, parseAmount(item.perStoDiviAmt())));
                }
            } catch (DateTimeParseException e) {
                log.warn("[배당배치] 날짜 파싱 실패 — skip shtCd={} recordDate={} diviPayDt={}",
                        item.shtCd(), item.recordDate(), item.diviPayDt());
            }
        }

        if (!events.isEmpty()) {
            calendarFeignClient.upsertStockEvents(events);
        }
    }
}
