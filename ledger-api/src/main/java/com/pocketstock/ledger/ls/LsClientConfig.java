package com.pocketstock.ledger.ls;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * LS 오픈API 호출용 공용 RestClient. 토큰 발급·시세 클라이언트가 공유한다.
 * 외부 게이트웨이 지연으로 요청 스레드가 매달리지 않도록 connect/read 타임아웃을 둔다.
 */
@Configuration
public class LsClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    @Bean
    public RestClient lsRestClient(LsApiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
