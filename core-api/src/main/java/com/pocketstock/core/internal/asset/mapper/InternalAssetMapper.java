package com.pocketstock.core.internal.asset.mapper;

import com.pocketstock.core.internal.asset.dto.LinkedAccountSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface InternalAssetMapper {

    List<LinkedAccountSummary> findLinkedAccountsByUserAndIds(
            @Param("userId") Long userId,
            @Param("ids") List<Long> ids
    );

    // 외화(USD) 지갑 목록 — 잔돈 수집의 FX 소스(전액 입금 대상). id+잔액 반환(수집 후 잔액 차감에 사용)
    List<LinkedAccountSummary> findUsdWallets(@Param("userId") Long userId);

    // id, amount 두 컬럼만 반환 — 서비스에서 라운드업 계산
    List<Map<String, Object>> findUncollectedCardTxs(
            @Param("userId") Long userId,
            @Param("linkedAccountId") Long linkedAccountId
    );

    int markRoundupCollected(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    BigDecimal findPointBalance(
            @Param("userId") Long userId,
            @Param("linkedAccountId") Long linkedAccountId
    );

    // 끝전 수집 확정 — 연동 계좌 잔액에서 수집액만큼 차감(원천을 닫아 재수집 방지)
    int deductAccountBalance(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("amount") BigDecimal amount
    );

    // 포인트 수집 확정 — 연동 포인트 잔액에서 수집액만큼 차감
    int deductPointBalance(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("amount") BigDecimal amount
    );
}
