package com.pocketstock.ledger.exchange.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 환전 정책 설정 — 적용환율 산정에 쓰는 스프레드·우대율.
 * 적용환율 = 매매기준율 × (1 ± s×(1−p)). 비용은 환율에 내재(별도 수수료 없음, fee=0).
 *
 * <p>{@code spread} = 통화별 전신환 스프레드율(예 USD 0.96%),
 * {@code preferential-rate} = 환율 우대율(예 90%: 기본70+계좌10+이벤트10).
 * 우대율은 은행 사정에 따라 변동 → 설정값으로 관리. (근거 backend#54)
 */
@Component
@ConfigurationProperties(prefix = "exchange")
@Getter
@Setter
public class ExchangeProperties {

    /** 통화별 전신환 스프레드율 (예: {USD=0.0096}). */
    private Map<String, BigDecimal> spread;

    /** 환율 우대율 (0~1, 예: 0.90). 스프레드를 이 비율만큼 할인. */
    private BigDecimal preferentialRate;

    /** 외부 환율 폴백 소스(야후) URL — WS 첫 틱 전 부팅 시드·캐시 미스 시 최후의 수단. */
    private String fxSourceUrl;
}
