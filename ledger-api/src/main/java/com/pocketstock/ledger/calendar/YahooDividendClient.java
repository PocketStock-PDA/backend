package com.pocketstock.ledger.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 야후 파이낸스 차트 API에서 해외 종목의 배당 데이터를 끌어온다 — KIS 예탁원(국내 전용)이 못 주는
 * 해외 배당의 보완 소스. {@link com.pocketstock.ledger.exchange.client.YahooFxClient 환율 폴백}과
 * 동일하게 chart 엔드포인트만 쓴다(crumb·쿠키 불필요 — 서버에서 안정적으로 뚫림).
 *
 * <p><b>chart가 주는 것</b>: {@code events.dividends}의 과거 배당 {(amount, date)} 목록(amount=주당
 * 현금배당금 통화단위, date=<b>배당락일</b>)과 {@code meta}(통화·현재가). <b>못 주는 것</b>: 미래
 * 배당락/지급일·정확한 지급일. 그래서 다음 배당락일은 과거 배당 주기(분기)로 투영하고, 지급일은
 * 따로 두지 않는다(quoteSummary가 줄 수 있으나 crumb 차단으로 미사용).
 *
 * <p>조회 실패(타임아웃·차단·형식 변경)는 <b>예외</b>로 올려 배치가 재시도하게 하고, 배당 이력이
 * 없을 때만 {@code null}을 돌려 건너뛴다(실패와 결손을 구분).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooDividendClient {

    private static final String CHART_BASE =
            "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String QUERY = "?range=1y&interval=1d&events=div";
    private static final ZoneId UTC = ZoneId.of("UTC");
    /** 과거 배당 1건뿐이라 주기를 못 구할 때의 기본 간격(분기 배당 가정). */
    private static final int DEFAULT_CADENCE_DAYS = 91;

    @Qualifier("yahooRestClient")
    private final RestClient yahooRestClient;

    /**
     * 종목 1개의 배당 스냅샷 조회. 배당 이력이 없으면 {@code null}을 돌려준다.
     *
     * <p>조회 실패(타임아웃·4xx/5xx·차단·응답 형식 변경)는 <b>예외를 던진다</b> — 배치가 이력 없음(null)과
     * 구분해 재시도(BootSyncRetry)할 수 있게. 삼키면 부팅 시 일시 장애가 영구 결손으로 굳는다.
     *
     * @param symbol 해외 종목코드(예 {@code VZ})
     */
    public YahooDividend fetch(String symbol) {
        JsonNode root = yahooRestClient.get()
                .uri(URI.create(CHART_BASE + symbol + QUERY))
                .retrieve()
                .body(JsonNode.class);   // 4xx/5xx·타임아웃은 RestClient가 예외 전파

        JsonNode result = root == null ? null : root.path("chart").path("result").path(0);
        if (result == null || result.isMissingNode() || result.path("meta").isMissingNode()) {
            // 유효 JSON이지만 기대 구조가 아님 = 차단/포맷 변경 → 실패로 보고 예외(이력 없음과 구분)
            throw new IllegalStateException("야후 차트 응답 형식 예상 밖: " + symbol);
        }
        String currency = result.path("meta").path("currency").asText(null);

        JsonNode divs = result.path("events").path("dividends");
        if (divs.isMissingNode() || !divs.fields().hasNext()) {
            log.info("[해외배당] {} 배당 이력 없음 — skip", symbol);
            return null;   // 정상 응답이나 배당 이력 없음 = 건너뜀(실패 아님)
        }

        // {ts -> {amount, date}} → 날짜 오름차순 정렬
        List<long[]> dates = new ArrayList<>();   // [epochSec]
        List<BigDecimal> amounts = new ArrayList<>();
        divs.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.path("amount").isNumber() && v.path("date").isNumber()) {
                dates.add(new long[]{v.path("date").asLong()});
                amounts.add(new BigDecimal(v.path("amount").asText()));
            }
        });
        if (dates.isEmpty()) {
            return null;   // 이력 노드는 있으나 유효 항목 없음 = 건너뜀
        }
        // 정렬 인덱스
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) idx.add(i);
        idx.sort((a, b) -> Long.compare(dates.get(a)[0], dates.get(b)[0]));

        int lastI = idx.get(idx.size() - 1);
        LocalDate recentExDate = toDate(dates.get(lastI)[0]);
        BigDecimal recentAmount = amounts.get(lastI);

        LocalDate nextExDate = projectNextExDate(recentExDate, cadenceDays(idx, dates));

        return new YahooDividend(symbol, currency, recentAmount, recentExDate, nextExDate);
    }

    /** 연속 배당락일 간격의 중앙값(일). 1건뿐이면 기본 분기값. */
    private static int cadenceDays(List<Integer> sortedIdx, List<long[]> dates) {
        if (sortedIdx.size() < 2) {
            return DEFAULT_CADENCE_DAYS;
        }
        List<Long> diffs = new ArrayList<>();
        for (int i = 1; i < sortedIdx.size(); i++) {
            long prev = dates.get(sortedIdx.get(i - 1))[0];
            long cur = dates.get(sortedIdx.get(i))[0];
            diffs.add((cur - prev) / 86_400L);
        }
        diffs.sort(Long::compareTo);
        return diffs.get(diffs.size() / 2).intValue();
    }

    /** 마지막 배당락일 + 주기를 오늘 이후로 굴려 다음 배당락일을 잡는다. */
    private static LocalDate projectNextExDate(LocalDate recentExDate, int cadenceDays) {
        LocalDate today = LocalDate.now(UTC);
        LocalDate next = recentExDate;
        while (!next.isAfter(today)) {
            next = next.plusDays(cadenceDays);
        }
        return next;
    }

    private static LocalDate toDate(long epochSec) {
        return Instant.ofEpochSecond(epochSec).atZone(UTC).toLocalDate();
    }

    /**
     * 야후 배당 스냅샷.
     *
     * @param symbol        종목코드
     * @param currency      배당 통화(예 USD)
     * @param perShareAmount 최근 1주당 현금배당금(통화단위, 원화 아님)
     * @param recentExDate  최근(과거) 배당락일
     * @param nextExDate    투영한 다음 배당락일(오늘 이후)
     */
    public record YahooDividend(
            String symbol,
            String currency,
            BigDecimal perShareAmount,
            LocalDate recentExDate,
            LocalDate nextExDate
    ) {}
}
