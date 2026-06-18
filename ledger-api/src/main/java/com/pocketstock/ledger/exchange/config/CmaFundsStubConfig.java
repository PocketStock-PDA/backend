package com.pocketstock.ledger.exchange.config;

import com.pocketstock.ledger.exchange.port.CmaFundsPort;
import com.pocketstock.ledger.exchange.port.DevCmaFundsAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CmaFundsPort 실 어댑터(CMA 도메인, 담당: 강문군)가 없을 때만 로컬 테스트용 스텁을 등록.
 * 실 어댑터가 {@code @Component}로 들어오면 이 빈은 비활성화된다(@ConditionalOnMissingBean).
 *
 * <p>{@code @ConditionalOnMissingBean}은 {@code @Component} 클래스가 아니라 <b>{@code @Bean}
 * 메서드</b>에 둬야 신뢰성 있게 동작한다 — 컴포넌트 스캔 클래스에 붙이면 평가 시점이 일러
 * 등록 자체가 누락될 수 있다.
 */
@Configuration
public class CmaFundsStubConfig {

    @Bean
    @ConditionalOnMissingBean(CmaFundsPort.class)
    public CmaFundsPort devCmaFundsAdapter() {
        return new DevCmaFundsAdapter();
    }
}
