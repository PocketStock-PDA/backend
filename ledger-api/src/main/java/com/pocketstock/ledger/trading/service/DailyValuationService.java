package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.DailyValuation;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.dto.DailyValuationResponse;
import com.pocketstock.ledger.trading.dto.StockPriceResponse;
import com.pocketstock.ledger.trading.mapper.DailyValuationMapper;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 일별 평가 스냅샷 (daily_valuations) — 적재 배치(BATCH-002) + 추이 조회. holdings는 lean이라 평가·수익률 추이를
 * 담을 곳이 없어 매일 종가로 박제(차트·히스토리). **종가 기준 native 평가손익** — 환차손익(현재환율)은 제외(live /holdings 몫).
 *
 * <p>적재: 매일 07:00 KST 1회. 미국장 마감(~06시) 후라 국내 전일 종가 + 해외 직전세션 종가가 모두 캐시에 확정돼 있고,
 * 국내 자동모으기/트리거(09:10)보다 앞선다. 종가 소스 = {@link StockPriceService}(장 마감이라 캐시=동결가=종가).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyValuationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CURRENCY_KRW = "KRW";
    private static final int AMOUNT_SCALE = 4;
    private static final int RATE_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int DEFAULT_RANGE_DAYS = 90;

    private final HoldingMapper holdingMapper;
    private final DailyValuationMapper dailyValuationMapper;
    private final StockPriceService stockPriceService;

    /** 일별 평가 스냅샷 적재 — 매일 07:00 KST(미 장마감 후·국내 개장 전). */
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    public void snapshot() {
        run();
    }

    /** 전체 보유 × 종가 → daily_valuations upsert. (dev 수동 트리거 공용) 종목당 가격 1회 조회 캐싱. */
    public void run() {
        LocalDate today = LocalDate.now(KST);
        List<Holding> holdings = holdingMapper.findAllActive();
        Map<String, BigDecimal> priceCache = new HashMap<>();
        int ok = 0;
        int skip = 0;
        for (Holding h : holdings) {
            try {
                BigDecimal price = priceCache.computeIfAbsent(h.getStockCode(),
                        code -> fetchClosePrice(h.getUserId(), code, h.getCurrency()));
                if (price == null || price.signum() <= 0) {
                    skip++;
                    continue;   // 종가 못 구함 — 그 종목 스냅샷 스킵(추이에 그날 구멍)
                }
                dailyValuationMapper.upsert(buildValuation(h, price, today));
                ok++;
            } catch (Exception e) {
                skip++;
                log.warn("[BATCH-002] 스냅샷 실패 user={} stock={}", h.getUserId(), h.getStockCode(), e);
            }
        }
        log.info("[BATCH-002] daily_valuations {} 적재 — 성공 {} · 스킵 {}", today, ok, skip);
    }

    /** 종목 수익률·평가 추이(기간, eval_date asc) — 차트용. from/to 생략 시 최근 90일. */
    @Transactional(readOnly = true)
    public List<DailyValuationResponse> getTrend(Long userId, String stockCode, LocalDate from, LocalDate to) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        LocalDate end = to != null ? to : LocalDate.now(KST);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS);
        return dailyValuationMapper.findByUserAndStock(userId, stockCode, start, end).stream()
                .map(DailyValuationResponse::from)
                .toList();
    }

    /** 종가 = 시세 캐시 현재가(장 마감이면 동결가=종가). 통화로 국내/해외 라우팅. 실패 시 null. */
    private BigDecimal fetchClosePrice(Long userId, String stockCode, String currency) {
        try {
            StockPriceResponse p = CURRENCY_KRW.equals(currency)
                    ? stockPriceService.getDomesticPrice(userId, stockCode)
                    : stockPriceService.getOverseasPrice(userId, stockCode);
            return p == null ? null : p.currentPrice();
        } catch (Exception e) {
            log.warn("[BATCH-002] 종가 조회 실패 stock={}", stockCode, e);
            return null;
        }
    }

    /** 종가 기준 native 평가 계산. 무상주 등 avg=0이면 수익률 0(분모 0 가드). */
    private DailyValuation buildValuation(Holding h, BigDecimal closePrice, LocalDate today) {
        BigDecimal qty = h.getQuantity();
        BigDecimal avg = h.getAvgBuyPrice() == null ? BigDecimal.ZERO : h.getAvgBuyPrice();
        BigDecimal evalAmount = qty.multiply(closePrice).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal costBasis = qty.multiply(avg).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal profitRate = avg.signum() == 0
                ? BigDecimal.ZERO
                : closePrice.subtract(avg).divide(avg, 6, RoundingMode.HALF_UP)
                        .multiply(HUNDRED).setScale(RATE_SCALE, RoundingMode.HALF_UP);
        return DailyValuation.builder()
                .userId(h.getUserId())
                .stockCode(h.getStockCode())
                .evalDate(today)
                .quantity(qty)
                .closePrice(closePrice)
                .evalAmount(evalAmount)
                .profitAmount(evalAmount.subtract(costBasis))
                .profitRate(profitRate)
                .currency(h.getCurrency())
                .build();
    }
}
