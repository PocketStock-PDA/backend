package com.pocketstock.ledger.ls;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

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

    public LsTokenClient(LsApiProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
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

        LsTokenResponse res = restClient.post()
                .uri("/oauth2/token")
                .header(HttpHeaders.CONTENT_TYPE, FORM_CONTENT_TYPE)  // charset 미부착(중요)
                .body(body)
                .retrieve()
                .body(LsTokenResponse.class);

        if (res == null || !StringUtils.hasText(res.accessToken())) {
            throw new IllegalStateException("LS 접근토큰 발급 응답이 비어있음");
        }
        log.info("LS 접근토큰 발급 성공 (expires_in={}s)", res.expiresIn());
        return res;
    }

    private static String encodeForm(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
