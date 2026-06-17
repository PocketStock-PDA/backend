package com.pocketstock.user.member.dto;

import java.util.List;

/**
 * 약관 동의 등록 요청.
 * 약관 항목·termId·본문·버전은 프론트가 정적 관리하므로 termId와 동의 여부만 전달한다.
 */
public record TermsAgreeRequest(List<TermItem> terms) {

    public record TermItem(Integer termId, Boolean agreed) {
    }
}
