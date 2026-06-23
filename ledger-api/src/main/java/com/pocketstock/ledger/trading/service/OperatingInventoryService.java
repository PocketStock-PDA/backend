package com.pocketstock.ledger.trading.service;

import com.pocketstock.ledger.trading.mapper.OperatingInventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회사 옴니버스 재고 처리(복식부기 상대계정, 주식 leg). 유저 holdings 이동의 반대 짝 —
 * 매수 시 회사 재고 −qty, 매도 시 +qty. {@code operating_account.whole_qty}를 원자 upsert한다.
 *
 * <p>H1이 정한 비대칭을 따른다: 회사 현금은 journal+projection(operating_cash_*)이지만,
 * <b>주식은 projection만</b>(operating_account.whole_qty) 두고 이력은 orders가 담당한다
 * (유저쪽도 holdings=projection / 이력=orders로 동일). 음수 가드 없음 — 무한유동성 시뮬에서
 * 회사 순재고는 음수가 될 수 있다(시장 조달 선공급).
 */
@Service
@RequiredArgsConstructor
public class OperatingInventoryService {

    private final OperatingInventoryMapper operatingInventoryMapper;

    /**
     * 회사 재고 1건 반영 — whole_qty += signedQty(원자 upsert, 종목행 self-seeding).
     * 유저 holdings leg의 반대 부호로 호출한다(매수 시 −qty, 매도 시 +qty).
     */
    @Transactional
    public void record(String stockCode, int signedQty) {
        operatingInventoryMapper.applyWholeDelta(stockCode, signedQty);
    }

    /** 소수점 정산용 — 종목 firm 끝수재고(fractional_remainder). 없으면 0. */
    @Transactional(readOnly = true)
    public java.math.BigDecimal fractionalRemainder(String stockCode) {
        java.math.BigDecimal r = operatingInventoryMapper.findFractionalRemainder(stockCode);
        return r == null ? java.math.BigDecimal.ZERO : r;
    }

    /**
     * 소수점 firm 끝수재고 양방향 갱신 — fractional_remainder += delta(흡수 +/선공급 −), 음수가드.
     * 0행이면 가드 위반(총재고<0) = ceil/floor 산식 위반 버그 → 호출자가 롤백·알람.
     */
    @Transactional
    public void applyFractional(String stockCode, java.math.BigDecimal delta) {
        operatingInventoryMapper.seedRow(stockCode);
        if (operatingInventoryMapper.applyFractionalDelta(stockCode, delta) == 0) {
            throw new com.pocketstock.common.exception.BusinessException(
                    com.pocketstock.common.exception.ErrorCode.INTERNAL_ERROR,
                    "firm 끝수재고 음수 가드 위반 stockCode=" + stockCode + " delta=" + delta);
        }
    }
}
