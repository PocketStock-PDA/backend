package com.pocketstock.ledger.trading.port;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.port.DepositFundsPort;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.mapper.DepositMapper;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.service.DepositService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@link DepositFundsPort} 실 어댑터 (trading 도메인) — CMA→예수금 자금이동의 예수금 입금 레그를 처리한다.
 *
 * <p>market(DOMESTIC/OVERSEAS) → 위탁계좌 해석은 trading이 소유한다. 입금은 {@code IN_TRANSFER}로
 * {@link DepositService#record}(잔액 원자 갱신 + 불변 역사 append)에 위임해 {@code BUY}/{@code SELL}과
 * 동일한 예수금 원장 규칙을 탄다. 호출자({@code CmaTransferService})의 {@code @Transactional} 안에서
 * 실행되어 CMA 풀 차감과 한 단위로 커밋·롤백된다.
 */
@Component
@RequiredArgsConstructor
public class DepositFundsAdapter implements DepositFundsPort {

    private static final String TX_IN_TRANSFER = "IN_TRANSFER";   // CMA→예수금 입금(+)
    private static final String REF_TYPE = "DEPOSIT_TOPUP";

    private final SecuritiesAccountMapper accountMapper;
    private final DepositService depositService;
    private final DepositMapper depositMapper;

    @Override
    public BigDecimal creditDeposit(Long userId, String market, String currency,
                                    BigDecimal amount, String idempotencyKey) {
        SecuritiesAccount account = requireAccount(userId, market);
        // ref_id는 null — CMA 레그와의 페어링은 공유 멱등키 base(TOPUP:{key})로 추적한다(부모 테이블 미신설).
        return depositService.record(userId, account.getId(), TX_IN_TRANSFER, amount,
                currency, REF_TYPE, null, idempotencyKey);
    }

    @Override
    public BigDecimal depositBalance(Long userId, String market) {
        SecuritiesAccount account = requireAccount(userId, market);
        return depositMapper.findBalanceByAccount(account.getId());
    }

    private SecuritiesAccount requireAccount(Long userId, String market) {
        SecuritiesAccount account = accountMapper.findByUserIdAndMarket(userId, market);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "위탁계좌가 없어 예수금으로 이체할 수 없습니다.");
        }
        return account;
    }
}
