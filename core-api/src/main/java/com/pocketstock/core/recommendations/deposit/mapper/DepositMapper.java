package com.pocketstock.core.recommendations.deposit.mapper;

import com.pocketstock.core.recommendations.deposit.dto.DepositProductDto;
import com.pocketstock.core.recommendations.deposit.dto.DepositRolloverDto;
import com.pocketstock.core.recommendations.deposit.dto.DueCmaTransfer;
import com.pocketstock.core.recommendations.deposit.dto.RolloverSourceAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DepositMapper {

    /** 활성 예적금 상품 — 최고금리 높은 순. */
    List<DepositProductDto> findActiveProducts();

    /** 상품 단건 — 재예치 생성 시 금리·기간 확정용. 없으면 null. */
    DepositProductDto findProductById(@Param("id") Long id);

    /** 소유 계좌 존재 확인(user_id 가드). */
    int countAccount(@Param("userId") Long userId, @Param("accountId") Long accountId);

    /** 재예치 원천 예적금 — 같은 상품 재예치용 상품명·금리·기간 스냅샷. 없거나 미소유면 null. */
    RolloverSourceAccount findRolloverSource(@Param("userId") Long userId, @Param("accountId") Long accountId);

    /** 해당 계좌에 이미 활성(RESERVED) 재예치/CMA 이체 예약이 있는지 — 둘은 동시 불가, 하나만 허용. */
    int countActiveRolloversByAccount(@Param("userId") Long userId, @Param("accountId") Long accountId);

    /**
     * 만기 이자 입금(1회, 멱등) — 예적금 잔액에 만기 단리 이자를 더하고 interest_credited_at을 찍는다.
     * 이미 입금됐으면(interest_credited_at NOT NULL) 0행 → 중복 입금 차단. 만기 굴리기 집행 직전 호출.
     * @return 갱신 행수(1=이번에 입금, 0=이미 입금됨/대상 아님)
     */
    int creditMaturityInterestOnce(@Param("userId") Long userId, @Param("accountId") Long accountId);

    int insertRollover(@Param("userId") Long userId,
                       @Param("linkedBankAccountId") Long linkedBankAccountId,
                       @Param("maturityDate") LocalDate maturityDate,
                       @Param("productName") String productName,
                       @Param("productType") String productType,
                       @Param("amount") long amount,
                       @Param("baseRate") BigDecimal baseRate,
                       @Param("maxRate") BigDecimal maxRate,
                       @Param("periodMonths") int periodMonths);

    /** 유저의 재예치 기록 전체(최신순) — 전환내역용. */
    List<DepositRolloverDto> findRolloversByUser(@Param("userId") Long userId);

    /** 만기 도래한 'CMA 이체' 예약(RESERVED, product_type='CMA', maturity_date ≤ date) — ledger 스케줄러 집행 대상. */
    List<DueCmaTransfer> findDueCmaTransfers(@Param("date") LocalDate date);

    /** CMA 이체 예약 취소 — RESERVED인 CMA 항목만 CANCELLED로. user_id 소유권 검증 포함. */
    int cancelCmaRollover(@Param("userId") Long userId, @Param("id") Long id);

    /** CMA 이체 예약 상태 갱신 — EXECUTED면 executed_at도 기록. RESERVED에서만 전이(멱등). */
    int updateRolloverStatus(@Param("id") Long id, @Param("status") String status);
}
