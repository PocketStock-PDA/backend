package com.pocketstock.user.member.domain;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;

/**
 * 약관 정적 카탈로그.
 * 약관 본문·버전은 프론트가 정적 관리하고, 백엔드는 termId로 식별되는 약관의
 * 종류(termsType)·필수여부(required)·버전(version)을 거울처럼 보유해 동의 이력을 남긴다.
 * 약관 추가/버전업 시 이 enum을 갱신한다.
 */
public enum Term {

    SERVICE(1, "SERVICE_USE", true, "1.0"),   // 서비스 이용약관(필수)
    PRIVACY(2, "PRIVACY", true, "1.0"),        // 개인정보 수집·이용 동의(필수)
    MARKETING(3, "MARKETING", false, "1.0");   // 마케팅 정보 수신 동의(선택)

    private final int termId;
    private final String termsType;
    private final boolean required;
    private final String version;

    Term(int termId, String termsType, boolean required, String version) {
        this.termId = termId;
        this.termsType = termsType;
        this.required = required;
        this.version = version;
    }

    /** termId로 약관을 찾는다. 정의되지 않은 termId는 잘못된 요청. */
    public static Term fromId(int termId) {
        for (Term t : values()) {
            if (t.termId == termId) {
                return t;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 약관입니다: " + termId);
    }

    public int getTermId() {
        return termId;
    }

    public String getTermsType() {
        return termsType;
    }

    public boolean isRequired() {
        return required;
    }

    public String getVersion() {
        return version;
    }
}
