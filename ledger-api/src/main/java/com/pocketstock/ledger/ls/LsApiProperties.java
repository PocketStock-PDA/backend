package com.pocketstock.ledger.ls;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LS증권 오픈API 접속 설정. 시크릿(app-key/app-secret-key)은 커밋 금지 —
 * 환경변수 또는 application-local.yml(gitignore)로 주입.
 */
@Component
@ConfigurationProperties(prefix = "ls.api")
@Getter
@Setter
public class LsApiProperties {

    /** LS 오픈API REST 도메인 (예: https://openapi.ls-sec.co.kr:8080) */
    private String baseUrl;

    /**
     * LS 실시간 WebSocket 도메인 (REST와 별개 포트).
     * 운영 wss://openapi.ls-sec.co.kr:9443/websocket · 모의 :29443/websocket
     */
    private String realtimeUrl;

    /** 고객 앱Key */
    private String appKey;

    /** 고객 앱 비밀Key */
    private String appSecretKey;
}
