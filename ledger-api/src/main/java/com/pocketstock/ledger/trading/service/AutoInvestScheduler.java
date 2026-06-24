package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.ledger.trading.domain.AutoInvestExecution;
import com.pocketstock.ledger.trading.domain.AutoInvestStock;
import com.pocketstock.ledger.trading.dto.FractionalOrderRequest;
import com.pocketstock.ledger.trading.dto.SplitOrderResponse;
import com.pocketstock.ledger.trading.mapper.AutoInvestExecutionMapper;
import com.pocketstock.ledger.trading.mapper.AutoInvestStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 자동모으기 정기매수 집행기 — 정해진 시각에 도래 종목을 모아 기존 소수점 엔진({@code place(source=AUTO)})으로 매수한다.
 * 신규 체결엔진 없음 — 정수부=온주 즉시체결·끝수=소수 차수가 엔진 내부에서 split(결정⑨).
 *
 * <p>국내 09:10 / 해외 22:40 (KST 고정). 해외 22:40은 서머타임=개장후10분(live) / 겨울=개장전(동결가, 24/7 정책).
 * 매수 결과는 회차 로그(auto_invest_executions)에 성공(FILLED)/실패(FAILED) 1행씩 — 와이어 "모으기 내역".
 * 멱등키 {@code AUTO_{stockId}_{날짜}}로 멀티인스턴스 중복 매수 차단. ※ 부족분 CMA 자동충전은 후속 연결.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoInvestScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SOURCE_AUTO = "AUTO";
    private static final String TRIGGER_PERIODIC = "PERIODIC";
    private static final String SIDE_BUY = "BUY";
    private static final String STATUS_FILLED = "FILLED";
    private static final String STATUS_FAILED = "FAILED";

    private final AutoInvestStockMapper stockMapper;
    private final AutoInvestExecutionMapper executionMapper;
    private final FractionalOrderService fractionalOrderService;

    /** 국내 정기매수 — 매일 09:10 KST(개장 직후, 항상 장중). */
    @Scheduled(cron = "0 10 9 * * *", zone = "Asia/Seoul")
    public void runDomestic() {
        run("DOMESTIC");
    }

    /** 해외 정기매수 — 매일 22:40 KST(서머타임 개장후10분 / 겨울 개장전=동결가). */
    @Scheduled(cron = "0 40 22 * * *", zone = "Asia/Seoul")
    public void runOverseas() {
        run("OVERSEAS");
    }

    /** 시장별 도래 종목 집행 — 한 종목 실패가 배치를 멈추지 않게 종목 단위로 격리. */
    void run(String market) {
        LocalDate today = LocalDate.now(KST);
        int weekday = today.getDayOfWeek().getValue();   // 1=월 ~ 7=일
        int dayOfMonth = today.getDayOfMonth();
        List<AutoInvestStock> due = stockMapper.findActiveDue(market, weekday, dayOfMonth);
        log.info("[자동모으기] {} 정기매수 시작 — 대상 {}종목 ({})", market, due.size(), today);
        for (AutoInvestStock stock : due) {
            try {
                executeOne(stock, today);
            } catch (Exception e) {
                // executeOne 내부에서 못 잡은 예외(시스템 오류 등) — 다음 종목으로 계속.
                log.error("[자동모으기] 종목 {} 집행 실패(stockId={})", stock.getStockCode(), stock.getId(), e);
            }
        }
    }

    /** 종목 1건 매수 + 회차 로그. place()는 자체 트랜잭션, 로그는 별도 — 실패해도 회차로 남긴다. */
    private void executeOne(AutoInvestStock stock, LocalDate today) {
        int roundNo = nextRoundNo(stock.getId());
        String clientOrderId = "AUTO_" + stock.getId() + "_" + today.format(ORDER_DATE);
        FractionalOrderRequest req = new FractionalOrderRequest(
                clientOrderId, stock.getStockCode(), SIDE_BUY, stock.getAmountType(),
                stock.getBuyAmount(), stock.getBuyQuantity());
        try {
            SplitOrderResponse resp = fractionalOrderService.place(stock.getUserId(), req, SOURCE_AUTO);
            recordFilled(stock, roundNo, today, resp);
        } catch (BusinessException e) {
            // 잔액부족 등 비즈니스 실패 = 접수 실패. 주문은 안 생김(AUTO는 REJECTED 미기록) → 회차에 FAILED로.
            recordFailed(stock, roundNo, today, e.getMessage());
        }
    }

    private int nextRoundNo(Long autoInvestStockId) {
        Integer max = executionMapper.findMaxRoundNo(autoInvestStockId);
        return max == null ? 1 : max + 1;
    }

    private void recordFilled(AutoInvestStock stock, int roundNo, LocalDate today, SplitOrderResponse resp) {
        BigDecimal qty = nz(resp.wholeQty() == null ? null : BigDecimal.valueOf(resp.wholeQty()))
                .add(nz(resp.fractionalEstQty()));
        BigDecimal amount = nz(resp.wholeAmount()).add(nz(resp.fractionalHeld()));
        Long orderId = resp.wholeOrderId() != null ? resp.wholeOrderId() : resp.fractionalOrderId();
        saveExecution(AutoInvestExecution.builder()
                .autoInvestStockId(stock.getId())
                .roundNo(roundNo)
                .triggerSource(TRIGGER_PERIODIC)
                .side(SIDE_BUY)
                .execDate(today)
                .status(STATUS_FILLED)
                .orderId(orderId)
                .execAmount(amount)
                .execQuantity(qty)
                .currency(stock.getCurrency())
                .build());
    }

    private void recordFailed(AutoInvestStock stock, int roundNo, LocalDate today, String reason) {
        saveExecution(AutoInvestExecution.builder()
                .autoInvestStockId(stock.getId())
                .roundNo(roundNo)
                .triggerSource(TRIGGER_PERIODIC)
                .side(SIDE_BUY)
                .execDate(today)
                .status(STATUS_FAILED)
                .failReason(reason == null ? "" : (reason.length() > 50 ? reason.substring(0, 50) : reason))
                .currency(stock.getCurrency())
                .build());
    }

    /** 회차 로그 적재 — (stock, round_no) UNIQUE라 멀티인스턴스 중복이면 한쪽만 성공(나머지 무시). */
    private void saveExecution(AutoInvestExecution execution) {
        try {
            executionMapper.insert(execution);
        } catch (DuplicateKeyException e) {
            log.debug("[자동모으기] 회차 로그 중복(다른 인스턴스가 집행) — stockId={} round={}",
                    execution.getAutoInvestStockId(), execution.getRoundNo());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
