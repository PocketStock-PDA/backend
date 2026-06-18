package com.pocketstock.ledger.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 최신 USD/KRW 환율(매매기준율) 단일 소스 — LS CUR 실시간 틱을 Redis에 덮어쓴다.
 * REST(/api/exchange/rate)·환전 체결이 WS 구독 없이도 같은 값을 읽도록 하는 SSOT.
 *
 * <p>저장값은 LS CUR 체결가(매매기준율). 스프레드·우대 적용환율은
 * {@link ExchangeRatePolicy}가 이 기준율에서 파생한다.
 *
 * <p>TTL 없음(last-known 유지) — 외환시장 마감/주말엔 틱이 끊기므로 마지막 값을 유지하고
 * staleness는 {@code updatedAt}으로 노출한다. 멀티 인스턴스 공유 위해 Redis(backend#24 연계).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyRateCache {

    static final String KEY = "exchange:rate:usd-krw";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 실시간 틱 수신 시 최신 환율 덮어쓰기. */
    public void put(CurrencyRateResponse rate) {
        try {
            redis.opsForValue().set(KEY, objectMapper.writeValueAsString(rate));
        } catch (Exception e) {
            log.warn("환율 캐시 저장 실패: {}", e.getMessage());
        }
    }

    /** 최신 환율. 아직 한 틱도 못 받았으면 null(콜드스타트). */
    public CurrencyRateResponse get() {
        try {
            String json = redis.opsForValue().get(KEY);
            return json == null ? null : objectMapper.readValue(json, CurrencyRateResponse.class);
        } catch (Exception e) {
            log.warn("환율 캐시 조회 실패: {}", e.getMessage());
            return null;
        }
    }
}
