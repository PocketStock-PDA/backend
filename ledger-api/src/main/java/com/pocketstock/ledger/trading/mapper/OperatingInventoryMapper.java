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
}
