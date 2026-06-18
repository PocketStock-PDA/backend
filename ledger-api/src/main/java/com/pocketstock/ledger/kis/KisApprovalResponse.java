package com.pocketstock.ledger.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** KIS 실시간 웹소켓 접속키 발급(/oauth2/Approval) 응답. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisApprovalResponse(
        @JsonProperty("approval_key") String approvalKey
) {
}
