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
}
