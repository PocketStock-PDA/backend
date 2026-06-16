package com.pocketstock.ledger.trading.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.ls.LsApiProperties;
import com.pocketstock.ledger.ls.LsTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * LS증권 국내 시세 TR 호출. 토큰은 {@link LsTokenProvider}에서 주입받아 공유한다.
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

    public LsMarketClient(LsApiProperties props, LsTokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    /** t1102 국내 현재가 조회(KRX). */
    public LsT1102Response.OutBlock getDomesticPrice(String shcode) {
        return callT1102(shcode, true);
    }

    private LsT1102Response.OutBlock callT1102(String shcode, boolean allowRetry) {
        byte[] body = serialize(Map.of("t1102InBlock",
                Map.of("shcode", shcode, "exchgubun", EXCHANGE_KRX)));
        try {
            LsT1102Response res = restClient.post()
                    .uri(MARKET_DATA_PATH)
                    .header(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                    .header("tr_cd", "t1102")
                    .header("tr_cont", "N")
                    .header("tr_cont_key", "")
                    .header("mac_address", "")
                    .body(body)
                    .retrieve()
                    .body(LsT1102Response.class);

            if (res == null || res.outBlock() == null) {
                String msg = res != null ? res.rspMsg() : "LS 응답 없음";
                throw new BusinessException(ErrorCode.NOT_FOUND, "현재가 조회 실패: " + msg);
            }
            if (res.rspCd() != null && !SUCCESS_CODE.equals(res.rspCd())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "LS 오류(" + res.rspCd() + "): " + res.rspMsg());
            }
            // LS는 무효 종목코드에도 성공(00000) + 빈 블록(hname="")을 반환 → 종목명 없으면 미존재로 처리
            if (!StringUtils.hasText(res.outBlock().hname())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 종목코드: " + shcode);
            }
            return res.outBlock();

        } catch (HttpClientErrorException.Unauthorized e) {
            // 캐시 토큰이 만료 직전 거부될 수 있음 → 1회 강제 재발급 후 재시도
            if (allowRetry) {
                log.warn("LS 401 — 토큰 재발급 후 재시도");
                tokenProvider.refresh();
                return callT1102(shcode, false);
            }
            throw e;
        }
    }

    private byte[] serialize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("LS 요청 직렬화 실패", e);
        }
    }
}
