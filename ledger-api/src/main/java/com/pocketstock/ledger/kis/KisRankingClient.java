package com.pocketstock.ledger.kis;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.util.List;
import java.util.Map;

/**
 * KIS 거래대금 순위 REST 호출 — 가입보상 후보 종목 선정용.
 * 시세 전용 {@link KisMarketClient}와 달리 순위 API는 쿼리 파라미터가 TR마다 달라
 * (국내 FID_*, 해외 KEYB/EXCD/…), 임의 파라미터 Map을 받는 별도 클라이언트로 둔다.
 * 토큰·appkey/appsecret 헤더·401 재발급 재시도·장애 502 변환 패턴은 KisMarketClient와 동일.
 *
 * <p>국내·해외 순위 모두 모의투자 미지원 → 실전 토큰 필요.
 */
@Slf4j
@Component
public class KisRankingClient {

    /** 국내주식 거래량순위(국내주식-047). FID_BLNG_CLS_CODE=3 으로 거래금액순 정렬. */
    private static final String DOMESTIC_RANK_PATH = "/uapi/domestic-stock/v1/quotations/volume-rank";
    private static final String TR_DOMESTIC_RANK = "FHPST01710000";

    /** 해외주식 거래대금순위(해외주식-044). */
    private static final String OVERSEAS_RANK_PATH = "/uapi/overseas-stock/v1/ranking/trade-pbmn";
    private static final String TR_OVERSEAS_RANK = "HHDFS76320010";

    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String CUST_TYPE_INDIVIDUAL = "P";
    private static final String SUCCESS_CODE = "0";

    private final KisApiProperties props;
    private final KisTokenProvider tokenProvider;
    private final RestClient kisRestClient;

    public KisRankingClient(KisApiProperties props, KisTokenProvider tokenProvider, RestClient kisRestClient) {
        this.props = props;
        this.tokenProvider = tokenProvider;
        this.kisRestClient = kisRestClient;
    }

    /**
     * 국내 거래대금 상위 종목(거래금액순). KRX 전체 대상, 가격·거래량 필터 없음.
     * 최대 30건 반환(다음 조회 불가).
     */
    public List<KisDomesticRankResponse.Item> getDomesticTradeAmountRank() {
        Map<String, String> q = Map.ofEntries(
                Map.entry("FID_COND_MRKT_DIV_CODE", "J"),   // J:KRX
                Map.entry("FID_COND_SCR_DIV_CODE", "20171"),
                Map.entry("FID_INPUT_ISCD", "0000"),        // 0000: 전체
                Map.entry("FID_DIV_CLS_CODE", "0"),         // 0: 전체(보통주+우선주)
                Map.entry("FID_BLNG_CLS_CODE", "3"),        // 3: 거래금액순
                Map.entry("FID_TRGT_CLS_CODE", "111111111"),
                Map.entry("FID_TRGT_EXLS_CLS_CODE", "0000000000"),
                Map.entry("FID_INPUT_PRICE_1", ""),         // 가격 필터 없음(전체)
                Map.entry("FID_INPUT_PRICE_2", ""),
                Map.entry("FID_VOL_CNT", "")                // 거래량 필터 없음(전체)
        );
        KisDomesticRankResponse res = callGet(DOMESTIC_RANK_PATH, TR_DOMESTIC_RANK, q,
                KisDomesticRankResponse.class);
        verify(res, (res == null) ? null : res.output(),
                (res == null) ? null : res.rtCd(), (res == null) ? null : res.msg1(), "국내");
        return res.output();
    }

    /**
     * 해외 거래대금 상위 종목. excd=거래소(NAS/NYS/AMS …), 당일·전체 거래량·가격 무필터.
     */
    public List<KisOverseasRankResponse.Item> getOverseasTradeAmountRank(String excd) {
        Map<String, String> q = Map.ofEntries(
                Map.entry("KEYB", ""),
                Map.entry("AUTH", ""),
                Map.entry("EXCD", excd),
                Map.entry("NDAY", "0"),      // 0: 당일
                Map.entry("VOL_RANG", "0"),  // 0: 전체
                Map.entry("PRC1", ""),       // 가격 필터 없음(전체)
                Map.entry("PRC2", "")
        );
        KisOverseasRankResponse res = callGet(OVERSEAS_RANK_PATH, TR_OVERSEAS_RANK, q,
                KisOverseasRankResponse.class);
        verify(res, (res == null) ? null : res.output2(),
                (res == null) ? null : res.rtCd(), (res == null) ? null : res.msg1(), excd);
        return res.output2();
    }

    /** GET 공통 호출 — 401이면 토큰 1회 재발급 후 재시도. 외부 호출 장애는 모두 502로 변환. */
    private <T> T callGet(String path, String trId, Map<String, String> params, Class<T> type) {
        try {
            return getOnce(path, trId, params, type);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("KIS 401 — 토큰 재발급 후 재시도 (tr={})", trId);
            try {
                tokenProvider.refresh();
                return getOnce(path, trId, params, type);
            } catch (RestClientException retryEx) {
                throw external(trId, retryEx);
            }
        } catch (RestClientException e) {
            throw external(trId, e);
        }
    }

    private <T> T getOnce(String path, String trId, Map<String, String> params, Class<T> type) {
        return kisRestClient.get()
                .uri(uri -> {
                    UriBuilder b = uri.path(path);
                    params.forEach(b::queryParam);
                    return b.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                .header("appkey", props.getAppKey())
                .header("appsecret", props.getAppSecret())
                .header("tr_id", trId)
                .header("custtype", CUST_TYPE_INDIVIDUAL)
                .header(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE)
                .retrieve()
                .body(type);
    }

    private void verify(Object res, List<?> out, String rtCd, String msg1, String target) {
        if (res == null || out == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "거래대금 순위 조회 실패: KIS 응답 없음 (" + target + ")");
        }
        if (rtCd != null && !SUCCESS_CODE.equals(rtCd)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "KIS 오류(" + rtCd + "): " + msg1);
        }
    }

    private BusinessException external(String trId, RestClientException e) {
        log.error("KIS 순위 호출 실패(tr={}): {}", trId, e.getMessage());
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "KIS 순위 서버 호출 실패");
    }
}
