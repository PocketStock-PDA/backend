package com.pocketstock.ledger.ls;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LS /oauth2/token 응답.
 * ※ expires_in은 고정값(86400)이 아니라 당일 만료까지 남은 초 → 동적으로 캐싱 TTL에 사용.
 */
public record LsTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("scope") String scope
) {
}
