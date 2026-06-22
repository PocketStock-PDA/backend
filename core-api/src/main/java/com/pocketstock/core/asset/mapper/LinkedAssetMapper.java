package com.pocketstock.core.asset.mapper;

import com.pocketstock.core.asset.dto.DormantAccountResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 사용자 연동 자산(linked_*) 조회 전용 Mapper.
 * 내부 Feign용 InternalAssetMapper와 책임 분리(이쪽은 사용자 화면 조회).
 */
@Mapper
public interface LinkedAssetMapper {

    /** 휴면(is_dormant=true) 은행 계좌 목록. 잔액 큰 순. */
    List<DormantAccountResponse> findDormantAccounts(@Param("userId") Long userId);

    /**
     * 외화(USD) 지갑 잔액 합계 — 잔돈 스캔의 FX(환전 잔돈) 소스.
     * 휴면 계좌는 제외(휴면 해지 흐름이 별도 처리). 행 없으면 0 반환.
     */
    BigDecimal sumUsdWalletBalance(@Param("userId") Long userId);
}
