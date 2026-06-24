package com.pocketstock.core.mypage.dto;

/**
 * 마이페이지 설정 부분 변경. 변경분만 보내고(null=변경 안 함), 둘 다 null이면 400.
 */
public record UpdateMyPageSettingsRequest(
        Boolean cardChangeCollect,
        Boolean monthlySavingCollect
) {}
