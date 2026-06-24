package com.pocketstock.ledger.ls;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LS증권 국내 순위 TR 호출. 시세({@link com.pocketstock.ledger.trading.client.LsMarketClient})와 달리
 * 순위는 경로(/stock/high-item)·연속조회(idx)가 달라 별도 클라이언트로 둔다(KIS도 시세/순위 분리).
 * 토큰·토큰거부 재발급·장애 502 변환 패턴은 LsMarketClient와 동일.
 *
 * <p>t1463(거래대금상위)은 거래대금·시가총액을 함께 반환하므로 두 정렬(거래대금/시총)을 한 소스로 만든다.
 */
@Slf4j
@Component
public class LsRankingClient {

    private static final String HIGH_ITEM_PATH = "/stock/high-item";
    private static final String TR_TRADE_VALUE = "t1463"; // 거래대금상위
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String SUCCESS_CODE = "00000";
    /** LS 게이트웨이가 토큰 무효/만료를 알리는 rsp_cd — 401이 아니라 5xx 바디로도 내려온다. */
    private static final Set<String> TOKEN_REJECTED_CODES = Set.of("IGW00121");
    private static final String EXCHANGE_UNIFIED = "U"; // KRX+NXT 통합 — 시세/실시간과 동일 기준
    private static final String GUBUN_ALL = "0";        // 0: 전체(코스피+코스닥) — 유니버스 필터로 좁힌다
    private static final String JNIL_TODAY = "0";       // 0: 당일
    /** 대상제외2(jc_num2) — 상장지수펀드(ETF) 제외. KODEX·TIGER 레버리지/인버스 등이 빠진다(ETN=8은 미제외). */
    private static final int JC_NUM2_EXCLUDE_ETF = 1;
    /** 연속조회 최대 페이지 — 지연·rate limit(t1463 초당 1건) 보호용 상한. */
    private static final int MAX_PAGES = 5;

    private final LsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LsRankingClient(LsTokenProvider tokenProvider, ObjectMapper objectMapper, RestClient lsRestClient) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
        this.restClient = lsRestClient;
    }

    /**
     * 국내 거래대금 상위(t1463) 윈도우 조회. 거래대금 내림차순으로 maxRows까지 idx 연속조회로 누적한다.
     * 첫 페이지는 필수(실패 시 예외 전파), 이후 페이지는 best-effort(실패하면 모은 만큼 반환) — rate limit 보호.
     * 각 행은 거래대금(value)·시가총액(total)을 함께 들고 있어 두 정렬을 모두 만들 수 있다.
     */
    public List<LsT1463Response.Item> getDomesticTradeValueRanking(int maxRows) {
        List<LsT1463Response.Item> acc = new ArrayList<>();
        int idx = 0;
        String trCont = "N";
        String trContKey = "";

        for (int page = 0; page < MAX_PAGES && acc.size() < maxRows; page++) {
            ResponseEntity<LsT1463Response> entity;
            try {
                entity = callTr(idx, trCont, trContKey);
            } catch (BusinessException e) {
                if (acc.isEmpty()) {
                    throw e;     // 첫 페이지 실패 → 그대로 전파
                }
                log.warn("LS t1463 연속조회 중단(page={}): {}", page, e.getMessage());
                break;           // 이후 페이지 실패는 모은 만큼으로 진행
            }

            LsT1463Response res = entity.getBody();
            verify(res);
            List<LsT1463Response.Item> rows = (res.outBlock1() == null) ? List.of() : res.outBlock1();
            acc.addAll(rows);

            int nextIdx = (res.outBlock() == null) ? 0 : res.outBlock().idx();
            boolean more = "Y".equalsIgnoreCase(headerValue(entity, "tr_cont"));
            if (rows.isEmpty() || !more || nextIdx <= idx) {
                break;           // 더 없음/커서 정지 → 종료
            }
            idx = nextIdx;
            trCont = "Y";
            trContKey = headerValue(entity, "tr_cont_key");
        }

        return acc.size() > maxRows ? new ArrayList<>(acc.subList(0, maxRows)) : acc;
    }

    // ---- 공통 호출/검증 ----

    /** TR 호출 — 토큰 거부 시 1회 재발급 후 재시도. 외부 장애는 모두 502로 변환. */
    private ResponseEntity<LsT1463Response> callTr(int idx, String trCont, String trContKey) {
        byte[] payload = serialize(body(idx));
        try {
            return postOnce(payload, trCont, trContKey);
        } catch (HttpClientErrorException.Unauthorized e) {
            return retryAfterTokenRefresh(payload, trCont, trContKey);
        } catch (HttpServerErrorException e) {
            if (!isTokenRejected(e)) {
                throw external(e);
            }
            return retryAfterTokenRefresh(payload, trCont, trContKey);
        } catch (RestClientException e) {
            throw external(e);
        }
    }

    private ResponseEntity<LsT1463Response> retryAfterTokenRefresh(byte[] payload, String trCont, String trContKey) {
        log.warn("LS 토큰 거부 — 재발급 후 재시도 (tr={})", TR_TRADE_VALUE);
        try {
            tokenProvider.refresh();
            return postOnce(payload, trCont, trContKey);
        } catch (RestClientException retryEx) {
            throw external(retryEx);
        }
    }

    private ResponseEntity<LsT1463Response> postOnce(byte[] body, String trCont, String trContKey) {
        return restClient.post()
                .uri(HIGH_ITEM_PATH)
                .header(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                .header("tr_cd", TR_TRADE_VALUE)
                .header("tr_cont", trCont)
                .header("tr_cont_key", trContKey)
                .header("mac_address", "")
                .body(body)
                .retrieve()
                .toEntity(LsT1463Response.class);
    }

    /** t1463InBlock — 전체시장·당일·무필터, idx만 연속조회로 올린다. */
    private Object body(int idx) {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("gubun", GUBUN_ALL);
        in.put("jnilgubun", JNIL_TODAY);
        in.put("jc_num", 0);
        in.put("sprice", 0);
        in.put("eprice", 0);
        in.put("volume", 0);
        in.put("idx", idx);
        in.put("jc_num2", JC_NUM2_EXCLUDE_ETF);
        in.put("exchgubun", EXCHANGE_UNIFIED);
        return Map.of("t1463InBlock", in);
    }

    private void verify(LsT1463Response res) {
        if (res == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "LS 순위 응답 없음");
        }
        if (res.rspCd() != null && !SUCCESS_CODE.equals(res.rspCd())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "LS 오류(" + res.rspCd() + "): " + res.rspMsg());
        }
    }

    /** 5xx 응답 바디의 rsp_cd가 토큰 무효/만료 계열인지 판별. */
    private boolean isTokenRejected(HttpServerErrorException e) {
        try {
            String rspCd = objectMapper.readTree(e.getResponseBodyAsByteArray()).path("rsp_cd").asText(null);
            return TOKEN_REJECTED_CODES.contains(rspCd);
        } catch (Exception parseEx) {
            return false;
        }
    }

    private String headerValue(ResponseEntity<?> entity, String name) {
        String v = entity.getHeaders().getFirst(name);
        return (v == null) ? "" : v.trim();
    }

    private BusinessException external(RestClientException e) {
        log.error("LS 순위 호출 실패(tr={}): {}", TR_TRADE_VALUE, e.getMessage());
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "LS 순위 서버 호출 실패");
    }

    private byte[] serialize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("LS 요청 직렬화 실패", e);
        }
    }
}
