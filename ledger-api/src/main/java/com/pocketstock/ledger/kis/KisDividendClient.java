package com.pocketstock.ledger.kis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * KIS 예탁원 배당일정 조회 (HHKDB669102C0).
 * 실전 전용(모의 미지원). 배치에서만 호출한다.
 * 401 응답 시 토큰 1회 재발급 후 재시도한다(KisMarketClient와 동일 패턴).
 */
@Slf4j
@Component
public class KisDividendClient {

    private static final String PATH    = "/uapi/domestic-stock/v1/ksdinfo/dividend";
    private static final String TR_ID   = "HHKDB669102C0";
    private static final String SUCCESS = "0";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KisApiProperties props;
    private final KisTokenProvider tokenProvider;
    private final RestClient kisRestClient;

    public KisDividendClient(KisApiProperties props, KisTokenProvider tokenProvider,
                             RestClient kisRestClient) {
        this.props = props;
        this.tokenProvider = tokenProvider;
        this.kisRestClient = kisRestClient;
    }

    public List<KisDividendResponse.Item> fetchDividends(LocalDate from, LocalDate to) {
        return fetch("", from, to);
    }

    public List<KisDividendResponse.Item> fetchDividendsByStock(String stockCode, LocalDate from, LocalDate to) {
        return fetch(stockCode, from, to);
    }

    private List<KisDividendResponse.Item> fetch(String stockCode, LocalDate from, LocalDate to) {
        try {
            return fetchOnce(stockCode, from, to);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("KIS 배당일정 401 — 토큰 재발급 후 재시도");
            try {
                tokenProvider.refresh();
                return fetchOnce(stockCode, from, to);
            } catch (Exception retryEx) {
                log.error("KIS 배당일정 재시도 실패: {}", retryEx.getMessage());
                return List.of();
            }
        } catch (Exception e) {
            log.error("KIS 배당일정 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private List<KisDividendResponse.Item> fetchOnce(String stockCode, LocalDate from, LocalDate to) {
        KisDividendResponse res = kisRestClient.get()
                .uri(uri -> uri.path(PATH)
                        .queryParam("CTS",     "")
                        .queryParam("GB1",     "0")
                        .queryParam("F_DT",    from.format(FMT))
                        .queryParam("T_DT",    to.format(FMT))
                        .queryParam("SHT_CD",  stockCode)
                        .queryParam("HIGH_GB", "")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                .header("appkey",    props.getAppKey())
                .header("appsecret", props.getAppSecret())
                .header("tr_id",     TR_ID)
                .header("custtype",  "P")
                .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .retrieve()
                .body(KisDividendResponse.class);

        if (res == null || !SUCCESS.equals(res.rtCd())) {
            log.error("KIS 배당일정 조회 실패: {}", res == null ? "null" : res.msg1());
            return List.of();
        }
        return res.output1() != null ? res.output1() : List.of();
    }
}
