package com.pocketstock.core.asset.dto;

/**
 * 연동 가능 기관(카탈로그) 단건.
 * institution_master(카탈로그) + 해당 유저의 linked_institutions.link_status를 합쳐,
 * 연동 선택 화면에서 이미 연동된 기관인지(LINKED) 선택 가능한지(AVAILABLE)를 함께 보여준다.
 * 외부 식별자는 company_code로 통일한다(F-B). 내부 numeric id는 노출하지 않는다.
 */
public record InstitutionResponse(
        String category,      // BANK / CARD / SECURITIES / POINT
        String companyCode,   // SHINHAN_BANK / KB_CARD ... (외부 식별자)
        String companyName,
        String logoUrl,
        String linkStatus     // LINKED(연동됨) / AVAILABLE(선택 가능)
) {}
