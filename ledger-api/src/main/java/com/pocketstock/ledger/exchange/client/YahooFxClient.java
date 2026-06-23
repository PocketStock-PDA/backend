package com.pocketstock.ledger.exchange.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.pocketstock.ledger.exchange.config.ExchangeProperties;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 외부 환율 폴백 소스 — 야후 파이낸스 차트 API(USD/KRW = {@code KRW=X})에서 매매기준율을 끌어온다.
 * LS CUR 실시간(WS)이 SSOT이고, 이 클라이언트는 보조용 — 두 곳에서만 쓴다:
 *
 * <ul>
 *   <li>부팅 시드: WS 첫 틱 전 콜드스타트 구간을 메움({@code CurrencyRatePinner})</li>
 *   <li>소비 폴백: 캐시가 비었을 때 환전·매수 거부 대신 1회 직접 조회({@code CurrencyRateProvider})</li>
 * </ul>
 *
 * <p>차트 응답의 {@code chart.result[0].meta}만 읽는다(캔들 불필요): {@code regularMarketPrice}=환율,
 * {@code chartPreviousClose}로 전일대비 산출. 결과 형식은 WS 리스너와 동일한 {@link CurrencyRateResponse}로
 * 맞춰 소비처가 출처(WS/야후)를 구분하지 않게 한다. 실패(타임아웃·형식 변경·차단)는 예외 대신 {@code null}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFxClient {

    private static final String BASE = "USD";
    private static final String TARGET = "KRW";
    /** 전일대비 소수 2자리 — WS 환율 표기와 동일. */
    private static final int CHANGE_SCALE = 2;

    private final RestClient yahooRestClient;
    private final ExchangeProperties props;

    /** USD/KRW 환율 1회 조회. 실패하면 null(호출자가 폴백/시드 무해화 처리). */
    public CurrencyRateResponse fetchUsdKrw() {
        try {
            JsonNode root = yahooRestClient.get()
                    .uri(props.getFxSourceUrl())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode meta = root.path("chart").path("result").path(0).path("meta");
            JsonNode priceNode = meta.path("regularMarketPrice");
            if (!priceNode.isNumber()) {
                log.warn("야후 환율 응답에 regularMarketPrice 없음 — 폴백 실패");
                return null;
            }
            BigDecimal price = new BigDecimal(priceNode.asText());
            JsonNode prevNode = meta.path("chartPreviousClose");
            BigDecimal change = prevNode.isNumber()
                    ? price.subtract(new BigDecimal(prevNode.asText())).setScale(CHANGE_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            return new CurrencyRateResponse(BASE, TARGET, price, change,
                    LocalDateTime.now().toString());   // 수신 시각(WS 리스너와 동일 규칙)
        } catch (Exception e) {
            log.warn("야후 환율 조회 실패: {}", e.getMessage());
            return null;
        }
    }
}
