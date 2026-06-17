package com.pocketstock.user.member.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 약관 동의 이력(terms_agreements, DB A) 도메인.
 * 컬럼과 1:1 매핑되며, termsType·isRequired·termsVersion은 {@link Term} 카탈로그에서 채운다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermsAgreement {

    private Long id;
    private Long userId;
    private String termsType;
    private Boolean isRequired;
    private String termsVersion;
    private Boolean isAgreed;
    private LocalDateTime agreedAt;
    private String ip;
    private String channel;
}
