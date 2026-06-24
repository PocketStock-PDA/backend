package com.pocketstock.core.mypage.dto;

/**
 * 연동된 기관 조회 행(institution_master ∩ linked_institutions LINKED).
 */
public record LinkedInstitutionRow(
        String category,     // BANK / CARD / SECURITIES / POINT
        String companyCode,  // SHINHAN_BANK ...
        String companyName   // 신한은행 ...
) {}
