package com.pocketstock.ledger.ls;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LS증권 접근토큰 발급 호출(POST /oauth2/token, form-urlencoded).
 * 발급만 담당 — 캐싱은 {@link LsTokenProvider}.
 *
 * ※ LS 게이트웨이는 Content-Type에 charset이 붙으면 403(IGW00133)으로 거부한다.
 *   Spring FormHttpMessageConverter는 charset을 자동 부착하므로, 폼을 직접 인코딩한
 *   byte[]로 보내고 Content-Type을 charset 없이 명시한다.
 */
@Slf4j
@Component
public class LsTokenClient {

    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    private final LsApiProperties props;
    private final RestClient restClient;

    public LsTokenClient(LsApiProperties props, RestClient lsRestClient) {
        this.props = props;
        this.restClient = lsRestClient;
    }

    /** client_credentials로 접근토큰 신규 발급. */
    public LsTokenResponse issueToken() {
        if (!StringUtils.hasText(props.getAppKey()) || !StringUtils.hasText(props.getAppSecretKey())) {
            throw new IllegalStateException(
                    "LS app-key/app-secret-key 미설정 — 환경변수(LS_APP_KEY·LS_APP_SECRET_KEY) 또는 application-local.yml 주입 필요");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "client_credentials");
        params.put("appkey", props.getAppKey());
        params.put("appsecretkey", props.getAppSecretKey());
        params.put("scope", "oob");
        byte[] body = encodeForm(params).getBytes(StandardCharsets.UTF_8);

        // 어느 키/도메인으로 발급하는지 노출(머신 간 비교용) — 실전/모의 불일치(10001) 진단.
        // appkey는 앞4·뒤4만(시크릿 미노출). env var LS_APP_KEY가 application-local.yml을 덮을 수 있음.
        log.info("LS 토큰 발급 시도 — baseUrl={} appkey={}", props.getBaseUrl(), mask(props.getAppKey()));

        LsTokenResponse res;
        try {
            res = restClient.post()
                    .uri("/oauth2/token")
                    .header(HttpHeaders.CONTENT_TYPE, FORM_CONTENT_TYPE)  // charset 미부착(중요)
                    .body(body)
                    .retrieve()
                    .body(LsTokenResponse.class);
        } catch (RestClientResponseException e) {
            // LS 원본 에러 응답(상태코드·바디) 노출 — "실전/모의" 등 진짜 사유 확인.
            log.error("LS 토큰 발급 거부 — status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }

        if (res == null || !StringUtils.hasText(res.accessToken())) {
            throw new IllegalStateException("LS 접근토큰 발급 응답이 비어있음");
        }
        log.info("LS 접근토큰 발급 성공 (expires_in={}s)", res.expiresIn());
        return res;
    }

    /** appkey 마스킹 — 앞4·뒤4만(머신 간 키 비교용, 시크릿 미노출). */
    private static String mask(String key) {
        if (key == null || key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "…" + key.substring(key.length() - 4) + "(len=" + key.length() + ")";
    }

    private static String encodeForm(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
