package com.pocketstock.ledger.exchange;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.client.YahooFxClient;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 매매기준율 조회 진입점 — 캐시({@link CurrencyRateCache}, LS CUR SSOT) 우선,
 * 비어 있으면 외부 폴백({@link YahooFxClient})으로 메운다. 환율 조회·환전·매수 충당이 모두 이걸 통한다.
 *
 * <p>WS(LS CUR)가 정상이면 항상 캐시 히트 — 폴백은 콜드스타트/WS 장기 단절/Redis 유실 등
 * 캐시가 빈 예외 상황에서만 발동하는 최후의 수단이다. 폴백 성공 시 캐시에 적재해 이후 호출은 다시 캐시 히트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyRateProvider {

    private final CurrencyRateCache cache;
    private final YahooFxClient yahoo;

    /** 최신 환율. 캐시 미스면 야후 폴백 1회, 그것도 실패하면 502. */
    public CurrencyRateResponse current() {
        CurrencyRateResponse latest = cache.get();
        if (latest != null) {
            return latest;
        }
        CurrencyRateResponse fallback = yahoo.fetchUsdKrw();   // 최후의 수단(외부 직접 호출)
        if (fallback == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "환율 정보를 아직 받지 못했습니다.");
        }
        cache.put(fallback);
        log.warn("환율 캐시 미스 — 야후 폴백으로 충당: {}", fallback.exchangeRate());
        return fallback;
    }

    /** 부팅 시드 — 캐시가 비어 있을 때만 야후로 1회 적재(best-effort, 실패해도 무해). */
    public void seedIfEmpty() {
        if (cache.get() != null) {
            return;   // 이미 채워짐(멀티 인스턴스 공유 Redis 등) — 클로버 방지
        }
        CurrencyRateResponse seed = yahoo.fetchUsdKrw();
        if (seed != null) {
            cache.put(seed);
            log.info("환율 부팅 시드(야후) 적재: {}", seed.exchangeRate());
        } else {
            log.warn("환율 부팅 시드 실패 — WS 첫 틱·하트비트로 충당 예정");
        }
    }
}
