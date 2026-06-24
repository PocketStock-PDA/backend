package com.pocketstock.ledger.cma.service;

import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaAutoChargeSetting;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaAutoChargeSettingMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.port.CmaChargePort;
import com.pocketstock.ledger.trading.port.CmaAutoChargePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@link CmaAutoChargePort} 실 어댑터 (CMA 도메인) — 매수 충당 시 CMA 원화풀 부족분을 사용자 설정대로 은행에서 채운다.
 *
 * <p>자기 설정({@link CmaAutoChargeSetting}: ON/OFF·대상 은행계좌·1회 한도)을 읽어 충전 여부·금액을 판단하고,
 * 실제 은행 → CMA 이동은 공용 {@link CmaChargePort}(={@code CmaBankChargeService})에 위임한다 — 사용자 직접 충전과
 * 같은 원장 경로. 호출자(매수)의 트랜잭션에 합류하고, 거래 인증은 호출자가 이미 처리했다고 보고 재요구하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CmaAutoChargeAdapter implements CmaAutoChargePort {

    private static final String KRW = "KRW";

    private final CmaAutoChargeSettingMapper settingMapper;
    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;
    private final CmaChargePort chargePort;

    @Override
    public void ensureKrwPoolForBuy(Long userId, BigDecimal requiredKrw, Long orderId) {
        if (requiredKrw == null || requiredKrw.signum() <= 0) {
            return;
        }
        CmaAutoChargeSetting setting = settingMapper.findByUserId(userId);
        if (setting == null || !Boolean.TRUE.equals(setting.getIsEnabled())) {
            return;   // 자동충전 OFF — 매수가 알아서 부족으로 실패
        }
        Long sourceAccountId = setting.getSourceAccountRef();
        if (sourceAccountId == null) {
            return;   // 대상 은행계좌 미설정
        }
        CmaAccount cma = accountMapper.findByUserId(userId);
        if (cma == null) {
            return;   // CMA 계좌 없음
        }
        BigDecimal pool = poolBalance(cma.getId());
        BigDecimal deficit = requiredKrw.subtract(pool);
        if (deficit.signum() <= 0) {
            return;   // 원화풀로 충분 — 충전 불필요
        }
        // 1회 한도로 캡(미설정이면 부족분 전액). 캡돼 여전히 부족하면 이후 CMA→예수금 출금이 막혀 매수 실패(의도).
        BigDecimal charge = deficit;
        BigDecimal limit = setting.getMaxChargePerTx();
        if (limit != null && limit.signum() > 0 && charge.compareTo(limit) > 0) {
            charge = limit;
        }
        // 실제 은행 → CMA 이동(공용 포트). 은행 잔액 부족이면 INSUFFICIENT_BALANCE로 호출자 트랜잭션 롤백 → 매수 실패.
        chargePort.charge(userId, sourceAccountId, charge, "order:" + orderId + ":autocharge");
    }

    private BigDecimal poolBalance(Long cmaAccountId) {
        CmaBalance balance = balanceMapper.findByAccountIdAndCurrency(cmaAccountId, KRW);
        return balance == null ? BigDecimal.ZERO : balance.getBalance();
    }
}
