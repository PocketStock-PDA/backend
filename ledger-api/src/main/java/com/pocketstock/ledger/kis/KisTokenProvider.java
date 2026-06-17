package com.pocketstock.ledger.kis;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * KIS 접근토큰(access_token) 공급자 — 해외 시세 REST 호출의 Bearer 인증에 사용.
 * WS용 {@link KisApprovalKeyProvider}(approval_key)와는 별개의 토큰이다.
 *
 * <p>KIS는 발급 후 6시간 이내 재호출 시 직전 토큰을 그대로 돌려주고 잦은 발급을 제한하므로,
 * Redis에 캐싱하고 만료 여유(REFRESH_MARGIN)를 두고 재발급한다. TTL은 응답 expires_in 기반.
 */
@Slf4j
@Component
public class KisTokenProvider {

    static final String CACHE_KEY = "kis:access_token";
    private static final long REFRESH_MARGIN_SEC = 600;
    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final KisApiProperties props;
    private final RestClient kisRestClient;
    private final StringRedisTemplate redis;

    public KisTokenProvider(KisApiProperties props, RestClient kisRestClient, StringRedisTemplate redis) {
        this.props = props;
        this.kisRestClient = kisRestClient;
        this.redis = redis;
    }

    /** 유효한 접근토큰 반환(캐시 우선, 없으면 발급 후 캐싱). */
    public String getAccessToken() {
        String cached = redis.opsForValue().get(CACHE_KEY);
        return cached != null ? cached : issueAndCache();
    }

    /** 캐시 강제 무효화 후 재발급(토큰 거부 응답 시 호출). */
    public String refresh() {
        redis.delete(CACHE_KEY);
        return issueAndCache();
    }

    private synchronized String issueAndCache() {
        // 락 획득 사이 다른 스레드가 채웠을 수 있으니 재확인(인스턴스 내 stampede 방지)
        String cached = redis.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        KisTokenResponse res;
        try {
            res = kisRestClient.post()
                    .uri(TOKEN_PATH)
                    .header(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE)
                    .body(Map.of(
                            "grant_type", "client_credentials",
                            "appkey", props.getAppKey(),
                            "appsecret", props.getAppSecret()))
                    .retrieve()
                    .body(KisTokenResponse.class);
        } catch (RestClientException e) {
            log.error("KIS 접근토큰 발급 호출 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "KIS 접근토큰 발급 실패");
        }
        if (res == null || res.accessToken() == null || res.accessToken().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "KIS 접근토큰 응답이 비어 있음");
        }
        long ttl = Math.max(res.expiresIn() - REFRESH_MARGIN_SEC, 1);
        redis.opsForValue().set(CACHE_KEY, res.accessToken(), Duration.ofSeconds(ttl));
        log.info("KIS 접근토큰 캐시 갱신 (ttl={}s)", ttl);
        return res.accessToken();
    }
}
