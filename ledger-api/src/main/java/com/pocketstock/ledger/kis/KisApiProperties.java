package com.pocketstock.ledger.kis;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 한국투자증권(KIS) 오픈API 접속 설정 — 해외주식 실시간에 사용.
 * 시크릿(app-key/app-secret)은 커밋 금지 — 환경변수 또는 application-local.yml(gitignore)로 주입.
 */
@Component
@ConfigurationProperties(prefix = "kis.api")
@Getter
@Setter
public class KisApiProperties {

    /** KIS REST 도메인 (실전 https://openapi.koreainvestment.com:9443 / 모의 ...vts...:29443) */
    private String baseUrl;

    /** KIS 실시간 WebSocket 도메인 (실전 ws://ops.koreainvestment.com:21000 / 모의 :31000) */
    private String realtimeUrl;

    /** 앱키 */
    private String appKey;

    /** 앱시크릿 (Approval API에선 secretkey 라는 이름으로 보냄 — 값은 동일) */
    private String appSecret;
}
