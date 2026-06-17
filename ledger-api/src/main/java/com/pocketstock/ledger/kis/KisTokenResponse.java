package com.pocketstock.ledger.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** KIS 접근토큰 발급(/oauth2/tokenP) 응답. REST 호출 시 Bearer로 사용. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
}
