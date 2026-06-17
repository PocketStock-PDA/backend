package com.pocketstock.ledger.trading.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.ls.LsTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * LS증권 국내 시세 TR 호출. 토큰은 {@link LsTokenProvider}에서 주입받아 공유한다.
 * 모든 거래소 TR은 동일 경로(/stock/market-data)에 tr_cd 헤더만 바꿔 POST한다.
 * ※ 토큰 발급(form)과 달리 TR 호출의 Content-Type은 charset 포함이 정상.
 */
@Slf4j
@Component
public class LsMarketClient {

    private static final String MARKET_DATA_PATH = "/stock/market-data";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String SUCCESS_CODE = "00000";
    private static final String EXCHANGE_KRX = "K";

    private final LsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LsMarketClient(LsTokenProvider tokenProvider, ObjectMapper objectMapper, RestClient lsRestClient) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.restClient = lsRestClient;
    }

    /** t1102 국내 현재가 조회(KRX). */
    public LsT1102Response.OutBlock getDomesticPrice(String shcode) {
        LsT1102Response res = callTr("t1102",
                Map.of("t1102InBlock", Map.of("shcode", shcode, "exchgubun", EXCHANGE_KRX)),
                LsT1102Response.class);
        LsT1102Response.OutBlock ob = (res == null) ? null : res.outBlock();
        verify(ob, (ob == null) ? null : ob.hname(),
                (res == null) ? null : res.rspCd(), (res == null) ? null : res.rspMsg(), shcode, "현재가");
        return ob;
    }

    /** t8450 국내 (통합)현재가호가 조회(KRX). 온주 주문 화면 진입 시 스냅샷용. */
    public LsT8450Response.OutBlock getDomesticOrderbook(String shcode) {
        LsT8450Response res = callTr("t8450",
                Map.of("t8450InBlock", Map.of("shcode", shcode, "exchgubun", EXCHANGE_KRX)),
                LsT8450Response.class);
        LsT8450Response.OutBlock ob = (res == null) ? null : res.outBlock();
        verify(ob, (ob == null) ? null : ob.hname(),
                (res == null) ? null : res.rspCd(), (res == null) ? null : res.rspMsg(), shcode, "호가");
        return ob;
    }

    // ---- 공통 호출/검증 ----

    /** TR 공통 호출 — 401이면 토큰 1회 재발급 후 재시도. 외부 호출 장애는 모두 502로 변환. */
    private <T> T callTr(String trCd, Object body, Class<T> type) {
        byte[] payload = serialize(body);
        try {
            return postOnce(trCd, payload, type);
        } catch (HttpClientErrorException.Unauthorized e) {
            // 캐시 토큰이 만료 직전 거부될 수 있음 → 강제 재발급 후 1회 재시도.
            log.warn("LS 401 — 토큰 재발급 후 재시도 (tr={})", trCd);
            try {
                tokenProvider.refresh();
                return postOnce(trCd, payload, type);
            } catch (RestClientException retryEx) {
                throw external(trCd, retryEx);
            }
        } catch (RestClientException e) {
            throw external(trCd, e);
        }
    }

    private <T> T postOnce(String trCd, byte[] body, Class<T> type) {
        return restClient.post()
                .uri(MARKET_DATA_PATH)
                .header(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                .header("tr_cd", trCd)
                .header("tr_cont", "N")
                .header("tr_cont_key", "")
                .header("mac_address", "")
                .body(body)
                .retrieve()
                .body(type);
    }

    /**
     * 응답 공통 검증. LS는 무효 종목코드에도 성공(00000) + 빈 블록(hname="")을 반환하므로
     * 종목명 유무로 미존재를 판별한다.
     */
    private void verify(Object outBlock, String hname, String rspCd, String rspMsg, String shcode, String what) {
        if (outBlock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, what + " 조회 실패: " + (rspMsg != null ? rspMsg : "LS 응답 없음"));
        }
        if (rspCd != null && !SUCCESS_CODE.equals(rspCd)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "LS 오류(" + rspCd + "): " + rspMsg);
        }
        if (!StringUtils.hasText(hname)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + shcode);
        }
    }

    /** 외부(LS) 호출 장애를 502로 변환. */
    private BusinessException external(String trCd, RestClientException e) {
        log.error("LS 시세 호출 실패(tr={}): {}", trCd, e.getMessage());
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "LS 시세 서버 호출 실패");
    }

    private byte[] serialize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("LS 요청 직렬화 실패", e);
        }
    }
}
