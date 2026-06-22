package com.pocketstock.ledger.trading.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperatingInventoryMapper {

    /**
     * 회사 옴니버스 재고(정수 주식) 원자 upsert — whole_qty += delta. 종목행 없으면 생성(self-seeding).
     * ※ 음수 가드 없음(회사 순재고는 음수 가능 = 무한유동성 시뮬에서 회사가 시장 조달로 선공급).
     *   유저 holdings의 반대 부호 짝 — 매수 시 −, 매도 시 +.
     * @return 갱신/생성된 행 수
     */
    int applyWholeDelta(@Param("stockCode") String stockCode, @Param("delta") int delta);

    /** 종목별 회사 순재고(operating_account.whole_qty). 없으면 null. (recon·검증용) */
    Integer findWholeQtyByStock(@Param("stockCode") String stockCode);

    /** 종목행 보장 — 없으면 생성(whole_qty=0, fractional_remainder=0). 있으면 무시(INSERT IGNORE). */
    int seedRow(@Param("stockCode") String stockCode);

    /**
     * 소수점 firm 끝수재고 원자 갱신(양방향) — fractional_remainder += delta, 음수가드(잔여+delta≥0).
     * 매수 흡수(+)·매도 흡수(+)·선공급(−) 모두 처리. 0행이면 가드 위반(총재고<0, ceil/floor 산식 위반 = 버그).
     * @return 갱신 행 수(0이면 가드에 막힘)
     */
    int applyFractionalDelta(@Param("stockCode") String stockCode,
                             @Param("delta") java.math.BigDecimal delta);

    /** 종목별 firm 끝수재고(operating_account.fractional_remainder). 없으면 null. */
    java.math.BigDecimal findFractionalRemainder(@Param("stockCode") String stockCode);
}
