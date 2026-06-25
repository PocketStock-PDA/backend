package com.pocketstock.core.asset.dto;

/** 연동 카드 단건 — 잔돈 모으기 카드 등록 선택 화면용 (GET /api/assets/cards). */
public record LinkedCardResponse(
        Long cardId,
        String cardName,
        String cardType,      // CREDIT / CHECK
        String maskedNo,
        String companyName
) {}
