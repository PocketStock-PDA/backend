package com.pocketstock.ledger.kis;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * KIS 실시간 웹소켓 접속키(approval_key) 공급자 — 해외 실시간 WebSocket 인증에 사용.
 * REST access_token과 별개로, /oauth2/Approval로 발급받아 WS 첫 프레임 헤더에 넣는다.
 * 24h 유효하지만 세션당 1회만 쓰므로 Redis에 캐싱하고 만료 여유를 두고 재발급한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisApprovalKeyProvider {

    static final String CACHE_KEY = "kis:approval_key";
    private static final Duration CACHE_TTL = Duration.ofHours(23);
    private static final String APPROVAL_PATH = "/oauth2/Approval";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final KisApiProperties props;
    private final RestClient kisRestClient;
    private final StringRedisTemplate redis;

    /** 유효한 approval_key 반환(캐시 우선, 없으면 발급 후 캐싱). */
    public String getApprovalKey() {
        String cached = redis.opsForValue().get(CACHE_KEY);
        return cached != null ? cached : issueAndCache();
    }

    /** 캐시 강제 무효화 후 재발급(세션 재연결로 키가 만료된 경우). */
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
        KisApprovalResponse res;
        try {
            res = kisRestClient.post()
                    .uri(APPROVAL_PATH)
                    .header(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE)
                    .body(Map.of(
                            "grant_type", "client_credentials",
                            "appkey", props.getAppKey(),
                            "secretkey", props.getAppSecret())) // Approval은 secretkey 라는 키명 사용
                    .retrieve()
                    .body(KisApprovalResponse.class);
        } catch (RestClientException e) {
            log.error("KIS approval_key 발급 호출 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "KIS 접속키 발급 실패");
        }
        if (res == null || res.approvalKey() == null || res.approvalKey().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "KIS 접속키 응답이 비어 있음");
        }
        redis.opsForValue().set(CACHE_KEY, res.approvalKey(), CACHE_TTL);
        log.info("KIS approval_key 캐시 갱신 (ttl={}h)", CACHE_TTL.toHours());
        return res.approvalKey();
    }
}
