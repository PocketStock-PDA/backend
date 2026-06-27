package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.ledger.client.CalendarFeignClient;
import com.pocketstock.ledger.client.dto.DividendPayoutScheduleView;
import com.pocketstock.ledger.lifecycle.LedgerActivation;
import com.pocketstock.ledger.trading.domain.DividendPayout;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.mapper.DividendReinvestSettingMapper;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 배당 지급·재투자 집행기 — 지급일이 도래한 배당을 보유자에게 CMA로 지급하고, DRIP ON 종목은 받은 배당으로 재투자한다.
 *
 * <p>매일 09:00 KST(만기매수 09:10 직전). 그날 {@code DIVIDEND_PAY} 일정(core 캘린더)을 종목별로 받아
 * 보유자(국내·KRW)마다 ① 지급(보유수량×주당배당금 → CMA 입금, 항상) → ② DRIP ON이면 재투자({@code max(배당,1000)} 매수)한다.
 * 보유자 단위 격리(한 명 실패가 배치 안 멈춤). 멱등: 지급 로그 UNIQUE(유저·종목·지급일) + 매수 client_order_id.
 * 단일 활성 인스턴스({@link LedgerActivation}) 게이트(자동모으기·만기매수와 동형).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendPayoutScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CURRENCY_KRW = "KRW";
    private static final int FAIL_REASON_MAX = 100;

    private final CalendarFeignClient calendarFeignClient;
    private final HoldingMapper holdingMapper;
    private final DividendReinvestSettingMapper reinvestSettingMapper;
    private final DividendPayoutService payoutService;
    private final LedgerActivation activation;

    /** 배당 지급·재투자 — 매일 09:00 KST. cron·dev 수동 트리거 공용 진입점. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void run() {
        if (!activation.isActive()) {
            return;   // 비활성 색 — 활성 색이 집행.
        }
        LocalDate today = LocalDate.now(KST);
        List<DividendPayoutScheduleView> schedules = calendarFeignClient.getDividendPayouts(today);
        log.info("[배당지급] 집행 시작 — 지급 예정 {}종목 ({})", schedules.size(), today);
        for (DividendPayoutScheduleView schedule : schedules) {
            try {
                payoutStock(schedule, today);
            } catch (Exception e) {
                log.error("[배당지급] 종목 {} 지급 실패", schedule.stockCode(), e);
            }
        }
    }

    /** 한 종목 보유자 전원에게 지급 + DRIP 재투자 — 보유자 단위 격리. */
    private void payoutStock(DividendPayoutScheduleView schedule, LocalDate today) {
        List<Holding> holders = holdingMapper.findHoldersByStock(schedule.stockCode());
        for (Holding holder : holders) {
            if (!CURRENCY_KRW.equals(holder.getCurrency())) {
                continue;   // 배당 지급은 국내(KRW)만 — 해외는 추후.
            }
            try {
                payoutOne(schedule, today, holder);
            } catch (Exception e) {
                log.error("[배당지급] 종목 {} 유저 {} 지급 실패",
                        schedule.stockCode(), holder.getUserId(), e);
            }
        }
    }

    /** 보유자 1명 — 지급(항상) 후 DRIP ON이면 재투자. 재투자 실패는 FAILED로 남기고 배당금은 CMA 현금 잔류. */
    private void payoutOne(DividendPayoutScheduleView schedule, LocalDate today, Holding holder) {
        DividendPayout payout = payoutService.payOut(
                schedule.stockCode(), schedule.perShare(), today, holder);
        if (payout == null) {
            return;   // 이미 지급됐거나 지급액 0 — 스킵.
        }
        if (!Boolean.TRUE.equals(reinvestSettingMapper.isEnabled(holder.getUserId(), schedule.stockCode()))) {
            log.info("[배당지급] 유저 {} {} {}원 지급(현금 수령)",
                    holder.getUserId(), schedule.stockCode(), payout.getGrossAmount());
            return;   // DRIP OFF — CMA 현금으로 둠.
        }
        try {
            payoutService.reinvest(payout);
            log.info("[배당지급] 유저 {} {} {}원 지급 후 재투자 완료",
                    holder.getUserId(), schedule.stockCode(), payout.getGrossAmount());
        } catch (BusinessException e) {
            payoutService.markReinvestFailed(payout.getId(), truncate(e.getMessage()));
            log.warn("[배당지급] 유저 {} {} 재투자 실패 — {} (배당금은 CMA 현금 잔류)",
                    holder.getUserId(), schedule.stockCode(), e.getMessage());
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= FAIL_REASON_MAX ? message : message.substring(0, FAIL_REASON_MAX);
    }
}
