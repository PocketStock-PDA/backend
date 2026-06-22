package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.TradingRound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RoundMapper {

    /**
     * 현재 분 차수 find-or-create — INSERT … ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)로
     * 신규/기존 무관하게 {@code round.id}를 채운다(useGeneratedKeys). 동시 접수가 같은 분을 동시에
     * 만들어도 UNIQUE로 한 행에 수렴하며, REPEATABLE READ에서 재select가 null 나는 함정을 피한다.
     */
    int upsertCurrent(TradingRound round);

    /** (market, round_no, trade_date)로 차수 조회 — find-or-create 2단계. 없으면 null. */
    TradingRound findByKey(@Param("market") String market,
                           @Param("roundNo") String roundNo,
                           @Param("tradeDate") LocalDate tradeDate);

    /**
     * 집행 대상 차수 — 실행시각(execute_at) 도달 + status=OPEN. 스케줄러가 선점 전에 후보를 훑는다.
     * market 분기는 스케줄러에서. SSOT=DB.
     */
    List<TradingRound> findDueOpenRounds(@Param("now") java.time.LocalDateTime now);

    /**
     * 차수 단일 실행 선점(D3) — OPEN일 때만 EXECUTING으로. affected=1인 인스턴스만 집행(이중집행 차단).
     */
    int claimForExecution(@Param("id") Long id);

    /** 집행 완료 — EXECUTING→SETTLED. */
    int markSettled(@Param("id") Long id);

    /** 집행 실패 — EXECUTING→FAILED(복구스윕/모니터링 대상). */
    int markFailed(@Param("id") Long id);
}
