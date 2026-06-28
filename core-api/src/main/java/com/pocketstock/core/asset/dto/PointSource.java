package com.pocketstock.core.asset.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 연동 포인트 개별 출처(1P=1원) — '기타' 드릴다운 항목 표기용. */
@Data
public class PointSource {
    private String pointName;   // 마이신한포인트 / 네이버포인트 / 토스포인트
    private BigDecimal balance;
}
