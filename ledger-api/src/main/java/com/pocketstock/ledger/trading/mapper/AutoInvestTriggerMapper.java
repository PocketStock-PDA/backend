package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.AutoInvestTrigger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AutoInvestTriggerMapper {

    /** 트리거 등록/수정 — 종목당 종류별 1((stock_id, trigger_kind) UNIQUE)이라 같은 종류 재등록은 갱신(is_armed 리셋). */
    int upsert(AutoInvestTrigger trigger);

    /** 종목의 트리거 목록(매수/매도, 최대 2). */
    List<AutoInvestTrigger> findByStockId(@Param("autoInvestStockId") Long autoInvestStockId);

    /** 트리거 해제 — 종목 소유 검증은 호출자가(stock_id 함께 가드). */
    int deleteByIdAndStockId(@Param("id") Long id, @Param("autoInvestStockId") Long autoInvestStockId);

    /**
     * 평가 대상 활성 트리거(시장별) — auto_invest_stocks·settings 조인.
     * 전역 enabled·미중지 + 종목 active + 트리거 active. user_id·stock_code·market·account_id·currency 동봉.
     */
    List<AutoInvestTrigger> findActiveByMarket(@Param("market") String market);

    /** 발동 처리 — is_armed=false + last_fired_at 기록(에지). */
    int markFired(@Param("id") Long id, @Param("firedAt") LocalDateTime firedAt);

    /** 재무장 — 수익률이 조건 밖으로 나갔을 때 is_armed=true(에지). */
    int rearm(@Param("id") Long id);
}
