package com.pocketstock.ledger.kis;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * KIS 해외 시세 REST 호출. 토큰은 {@link KisTokenProvider}(access_token)에서 주입받아 Bearer로 사용한다.
 * 헤더에 appkey/appsecret/tr_id/custtype을 함께 싣고, 쿼리로 AUTH/EXCD/SYMB를 보낸다.
 * (LS의 {@code LsMarketClient}와 동일한 401 재시도·장애 변환 패턴.)
 */
@Slf4j
@Component
public class KisMarketClient {

    private static final String PRICE_DETAIL_PATH = "/uapi/overseas-price/v1/quotations/price-detail";
    private static final String TR_PRICE_DETAIL = "HHDFS76200200";
    private static final String ASKING_PRICE_PATH = "/uapi/overseas-price/v1/quotations/inquire-asking-price";
    private static final String TR_ASKING_PRICE = "HHDFS76200100";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String CUST_TYPE_INDIVIDUAL = "P";
    private static final String SUCCESS_CODE = "0";

    private final KisApiProperties props;
    private final KisTokenProvider tokenProvider;
    private final RestClient kisRestClient;

    public KisMarketClient(KisApiProperties props, KisTokenProvider tokenProvider, RestClient kisRestClient) {
        this.props = props;
        this.tokenProvider = tokenProvider;
        this.kisRestClient = kisRestClient;
    }

    /** 해외 현재가상세(HHDFS76200200). excd=거래소(NAS/NYS/AMS), symb=종목(AAPL). */
    public KisPriceDetailResponse.Output getOverseasPriceDetail(String excd, String symb) {
        KisPriceDetailResponse res = callGet(PRICE_DETAIL_PATH, TR_PRICE_DETAIL, excd, symb,
                KisPriceDetailResponse.class);
        KisPriceDetailResponse.Output out = (res == null) ? null : res.output();
        verify(res, out, (res == null) ? null : res.rtCd(), (res == null) ? null : res.msg1(), symb);
        return out;
    }

    /** 해외 현재가 호가(HHDFS76200100). 미국 10호가. output2 평면(pbid/pask 1~10). */
    public KisAskingPriceResponse getOverseasOrderbook(String excd, String symb) {
        KisAskingPriceResponse res = callGet(ASKING_PRICE_PATH, TR_ASKING_PRICE, excd, symb,
                KisAskingPriceResponse.class);
        verify(res, (res == null) ? null : res.output2(),
                (res == null) ? null : res.rtCd(), (res == null) ? null : res.msg1(), symb);
        return res;
    }

    /** GET 공통 호출 — 401이면 토큰 1회 재발급 후 재시도. 외부 호출 장애는 모두 502로 변환. */
    private <T> T callGet(String path, String trId, String excd, String symb, Class<T> type) {
        try {
            return getOnce(path, trId, excd, symb, type);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("KIS 401 — 토큰 재발급 후 재시도 (tr={})", trId);
            try {
                tokenProvider.refresh();
                return getOnce(path, trId, excd, symb, type);
            } catch (RestClientException retryEx) {
                throw external(trId, retryEx);
            }
        } catch (RestClientException e) {
            throw external(trId, e);
        }
    }

    private <T> T getOnce(String path, String trId, String excd, String symb, Class<T> type) {
        return kisRestClient.get()
                .uri(uri -> uri.path(path)
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", excd)
                        .queryParam("SYMB", symb)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                .header("appkey", props.getAppKey())
                .header("appsecret", props.getAppSecret())
                .header("tr_id", trId)
                .header("custtype", CUST_TYPE_INDIVIDUAL)
                .header(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE)
                .retrieve()
                .body(type);
    }

    private void verify(Object res, Object out, String rtCd, String msg1, String symb) {
        if (res == null || out == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "해외 시세 조회 실패: KIS 응답 없음 (" + symb + ")");
        }
        if (rtCd != null && !SUCCESS_CODE.equals(rtCd)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "KIS 오류(" + rtCd + "): " + msg1);
        }
    }

    private BusinessException external(String trId, RestClientException e) {
        log.error("KIS 시세 호출 실패(tr={}): {}", trId, e.getMessage());
        return new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "KIS 시세 서버 호출 실패");
    }
}
