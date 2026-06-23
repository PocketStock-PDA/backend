package com.pocketstock.ledger.exchange.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 환율 폴백 소스(야후 파이낸스) 호출용 RestClient.
 * WS(LS CUR) 첫 틱 전 부팅 시드 + 캐시 미스 시 최후의 수단으로만 쓰므로,
 * 돈 경로(@Transactional)에서 호출돼도 스레드가 매달리지 않게 타임아웃을 짧게 둔다.
 */
@Configuration
public class FxClientConfig {

    /** 폴백은 매수/매도 트랜잭션 안에서 불릴 수 있어 락 점유 최소화 위해 짧게. */
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 2_000;

    @Bean
    public RestClient yahooRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return RestClient.builder()
                .requestFactory(factory)
                // 야후는 기본 UA를 종종 차단 — 브라우저 UA로 우회.
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();
    }
}
