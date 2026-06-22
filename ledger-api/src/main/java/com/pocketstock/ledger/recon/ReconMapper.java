package com.pocketstock.ledger.recon;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 원장 정합성 검산 전용 읽기 매퍼(#96 item4) — 도메인 매퍼를 건드리지 않고, recon이 필요한
 * 집계를 한곳에서 수행한다. 모든 대상 테이블이 DB B(같은 datasource)라 한 매퍼로 횡단 조회 가능.
 *
 * <p>모든 쿼리는 {@code reconKey}(통화/종목코드)·{@code reconVal}(합계/잔액) 두 컬럼으로 정규화해
 * 반환한다(언더스코어 없는 별칭 — map-underscore-to-camel-case 영향 차단). {@link LedgerReconService}가
 * 통화/종목 무관하게 동일 로직으로 대사한다. 읽기 전용.
 */
@Mapper
public interface ReconMapper {

    /** 고객 CMA 환전 leg 통화별 합(ref_type=FX_TX). */
    List<Map<String, Object>> sumCmaByRefType(@Param("refType") String refType);

    /** 회사 현금 leg 통화별 합. refType=null이면 전체(정합 검산용), 값 주면 해당 ref만(보존 검산용). */
    List<Map<String, Object>> sumOperatingCashByRefType(@Param("refType") String refType);

    /** 회사 현금 projection 잔액(통화별). */
    List<Map<String, Object>> operatingCashBalances();

    /** 유저 예수금 leg 통화별 합(ref_type 필터). */
    List<Map<String, Object>> sumDepositByRefType(@Param("refType") String refType);

    /** 유저 보유 주식 종목별 수량 합. */
    List<Map<String, Object>> sumHoldingsByStock();

    /** 회사 옴니버스 재고 종목별 총수량 = whole_qty(정수주) + fractional_remainder(소수주). */
    List<Map<String, Object>> operatingAccountInventory();

    /** 유저 예수금 계좌별 거래 합(journal). reconKey=account_id. */
    List<Map<String, Object>> sumDepositByAccount();

    /** 유저 예수금 projection 잔액(계좌별). reconKey=account_id. */
    List<Map<String, Object>> accountBalances();

    /** 고객 CMA 계좌·통화별 거래 합(journal). reconKey=account_id:currency. */
    List<Map<String, Object>> sumCmaAll();

    /** 고객 CMA projection 잔액(계좌·통화별). reconKey=account_id:currency. */
    List<Map<String, Object>> cmaBalances();
}
