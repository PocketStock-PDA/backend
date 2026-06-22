package com.pocketstock.ledger.dart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * OpenDART 공시 목록 조회.
 * pblntf_ty=I (공정공시) 중 "잠정" 포함 공시 → 실적 발표일로 사용.
 */
@Slf4j
@Component
public class OpenDartClient {

    private static final String LIST_PATH = "/api/list.json";
    private static final String SUCCESS   = "000";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OpenDartProperties props;
    private final RestClient restClient;

    public OpenDartClient(OpenDartProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 종목코드 기준으로 조회 기간 내 잠정실적 공시 목록 반환.
     * 공정공시(I) 중 보고서명에 "잠정"이 포함된 항목만 걸러낸다.
     */
    public List<OpenDartDisclosureResponse.Item> fetchEarningsDisclosures(
            String stockCode, LocalDate from, LocalDate to) {
        // OpenDART는 미래 날짜 불허 — end_de를 오늘로 클램프
        LocalDate effectiveTo = to.isAfter(LocalDate.now()) ? LocalDate.now() : to;
        try {
            OpenDartDisclosureResponse res = restClient.get()
                    .uri(uri -> uri.path(LIST_PATH)
                            .queryParam("crtfc_key",  props.getKey())
                            .queryParam("stock_code", stockCode)
                            .queryParam("bgn_de",     from.format(FMT))
                            .queryParam("end_de",     effectiveTo.format(FMT))
                            .queryParam("page_count", "100")
                            .build())
                    .retrieve()
                    .body(OpenDartDisclosureResponse.class);

            if (res == null || !SUCCESS.equals(res.status())) {
                log.warn("[DART] 조회 실패 stockCode={} status={} message={}",
                        stockCode,
                        res == null ? "null" : res.status(),
                        res == null ? "null" : res.message());
                return List.of();
            }
            if (res.list() == null) return List.of();

            log.debug("[DART] stockCode={} 전체 공시 {}건: {}",
                    stockCode, res.list().size(),
                    res.list().stream().map(OpenDartDisclosureResponse.Item::reportNm).toList());

            return res.list().stream()
                    .filter(item -> item.reportNm() != null && item.reportNm().contains("잠정"))
                    .toList();

        } catch (RestClientException e) {
            log.error("[DART] 호출 실패 stockCode={}: {}", stockCode, e.getMessage());
            return List.of();
        }
    }
}
