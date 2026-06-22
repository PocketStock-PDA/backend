package com.pocketstock.ledger.calendar;

import com.pocketstock.ledger.client.CalendarFeignClient;
import com.pocketstock.ledger.client.dto.StockEventUpsertRequest;
import com.pocketstock.ledger.dart.OpenDartClient;
import com.pocketstock.ledger.dart.OpenDartDisclosureResponse;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenDART 잠정실적 공시 기반 실적발표 일정 배치.
 * 매주 월요일 새벽 3시, 전체 보유 종목의 최근 3개월 잠정실적 공시를 수집해
 * core-api stock_events(EARNINGS)에 upsert한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarningsBatchService {

    private static final String EVENT_TYPE = "EARNINGS";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HoldingMapper holdingMapper;
    private final OpenDartClient openDartClient;
    private final CalendarFeignClient calendarFeignClient;

    @Scheduled(cron = "0 0 3 * * MON", zone = "Asia/Seoul")
    public void syncEarningsEvents() {
        List<String> stockCodes = holdingMapper.findAllDistinctStockCodes();
        if (stockCodes.isEmpty()) {
            log.info("[실적배치] 보유 종목 없음 — skip");
            return;
        }

        log.info("[실적배치] 보유 종목 {}개 처리 시작", stockCodes.size());

        LocalDate from = LocalDate.now().minusMonths(3);
        LocalDate to   = LocalDate.now();

        List<StockEventUpsertRequest> events = new ArrayList<>();
        for (String stockCode : stockCodes) {
            List<OpenDartDisclosureResponse.Item> items =
                    openDartClient.fetchEarningsDisclosures(stockCode, from, to);

            for (OpenDartDisclosureResponse.Item item : items) {
                try {
                    LocalDate eventDate = LocalDate.parse(item.rceptDt(), FMT);
                    String reportNm = item.reportNm() == null ? "" : item.reportNm().trim();
                    String title  = item.corpName() + " " + normalizeReportName(reportNm);
                    String detail = reportNm;
                    events.add(new StockEventUpsertRequest(stockCode, EVENT_TYPE, eventDate, title, detail));
                } catch (DateTimeParseException e) {
                    log.warn("[실적배치] 날짜 파싱 실패 — skip stockCode={} rceptDt={}",
                            stockCode, item.rceptDt());
                }
            }

            throttle();
        }

        if (!events.isEmpty()) {
            calendarFeignClient.upsertStockEvents(events);
        }
        log.info("[실적배치] 완료 — {}건 처리", events.size());
    }

    /** 매수 체결 시 해당 종목만 즉시 수집 (Kafka 연동 예정). */
    public void syncByStockCode(String stockCode) {
        LocalDate from = LocalDate.now().minusMonths(3);
        LocalDate to   = LocalDate.now();

        List<OpenDartDisclosureResponse.Item> items =
                openDartClient.fetchEarningsDisclosures(stockCode, from, to);

        List<StockEventUpsertRequest> events = new ArrayList<>();
        for (OpenDartDisclosureResponse.Item item : items) {
            try {
                LocalDate eventDate = LocalDate.parse(item.rceptDt(), FMT);
                String rNm   = item.reportNm() == null ? "" : item.reportNm().trim();
                String title = item.corpName() + " " + normalizeReportName(rNm);
                events.add(new StockEventUpsertRequest(stockCode, EVENT_TYPE, eventDate, title, rNm));
            } catch (DateTimeParseException e) {
                log.warn("[실적배치] 날짜 파싱 실패 — skip stockCode={} rceptDt={}",
                        stockCode, item.rceptDt());
            }
        }

        if (!events.isEmpty()) {
            calendarFeignClient.upsertStockEvents(events);
        }
    }

    private static String normalizeReportName(String reportNm) {
        if (reportNm == null) return "실적발표";
        if (reportNm.contains("연간") || reportNm.contains("사업")) return "연간 잠정실적";
        if (reportNm.contains("반기"))                               return "반기 잠정실적";
        if (reportNm.contains("분기"))                               return "분기 잠정실적";
        return "잠정실적";
    }

    /** OpenDART 호출 간격 조절 (10 TPS 제한). */
    private static void throttle() {
        try {
            Thread.sleep(120);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
