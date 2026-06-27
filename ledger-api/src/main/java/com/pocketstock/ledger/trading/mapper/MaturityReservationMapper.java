package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.MaturityBuyReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MaturityReservationMapper {

    /** 예약 생성. (user_id, linked_bank_account_id, stock_code) UNIQUE — 중복 시 DuplicateKey. */
    int insert(MaturityBuyReservation reservation);

    /** 유저의 예약 전체(최신순) — 종목명 join. */
    List<MaturityBuyReservation> findByUserId(@Param("userId") Long userId);

    /** 단건 — 남의 예약 노출 금지(user_id 가드). */
    MaturityBuyReservation findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** (유저·계좌·종목) 1건 — 생성 시 기존 예약 확인용(취소/실패 건 되살리기 판단). UNIQUE라 0/1건. */
    MaturityBuyReservation findByUserAccountStock(@Param("userId") Long userId,
                                                  @Param("linkedBankAccountId") Long linkedBankAccountId,
                                                  @Param("stockCode") String stockCode);

    /** 취소·실패 예약 되살리기 — CANCELLED/FAILED를 새 만기일·금액으로 RESERVED 재활성(집행 흔적 초기화). */
    int revive(@Param("id") Long id,
               @Param("maturityDate") java.time.LocalDate maturityDate,
               @Param("buyAmount") java.math.BigDecimal buyAmount);

    /**
     * 만기 도래 예약(스케줄러용) — status=RESERVED & maturity_date ≤ today. 종목명 join.
     * 단일 활성 인스턴스만 호출(LedgerActivation 게이트).
     */
    List<MaturityBuyReservation> findDue(@Param("today") LocalDate today);

    /** 예약 취소(만기 전) — RESERVED 상태에서만, user_id 가드. 영향 행 0이면 취소 불가(이미 집행/취소). */
    int cancel(@Param("id") Long id, @Param("userId") Long userId);

    /** 집행 결과 반영 — status(EXECUTED/FAILED)·order_id·fail_reason·executed_at. 멱등: RESERVED에서만 전이. */
    int markExecuted(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("orderId") Long orderId,
                     @Param("failReason") String failReason);
}
