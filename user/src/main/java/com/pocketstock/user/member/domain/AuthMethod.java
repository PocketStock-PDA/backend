package com.pocketstock.user.member.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 간편인증 수단(user_auth_methods, DB A) 도메인.
 * (user_id, method_type) UNIQUE — 사용자당 PIN/PATTERN 각 1건. secret_hash는 BCrypt.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthMethod {

    private Long id;
    private Long userId;
    private String methodType;   // PIN / PATTERN
    private String secretHash;
    private Boolean isActive;
}
