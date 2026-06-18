package com.pocketstock.ledger.exchange.port;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link CmaFundsPort} <b>로컬 테스트용 스텁</b> — CMA 실 어댑터(담당: 강문군)가 없을 때만
 * {@link com.pocketstock.ledger.exchange.config.CmaFundsStubConfig}가 빈으로 등록한다
 * (@ConditionalOnMissingBean). 실 어댑터가 {@code @Component}로 들어오면 자동으로 비활성.
 *
 * <p>잔액은 <b>인메모리</b>(사용자별 KRW/USD)로 흉내내며, 첫 접근 시 시드 잔액을 채운다.
 * DB를 안 쓰므로 호출자 트랜잭션 롤백에 동참하지 않는다 — 그래서 체결 서비스는 fx 기록을 먼저
 * 적재(id 확보)하고 이 호출을 <b>마지막</b>에 둬, 이후 실패로 인한 불일치 여지를 없앤다.
 * 실서비스 의미(원장·잔액 정합·멱등)는 CMA 실 어댑터가 보장한다. 비번은 데모상 "1234"만 통과.
 */
@Slf4j
public class DevCmaFundsAdapter implements CmaFundsPort {

    private static final BigDecimal SEED_KRW = new BigDecimal("5000000");
    private static final BigDecimal SEED_USD = new BigDecimal("1000.00");
    private static final String DEV_PASSWORD = "1234";

    /** key = userId + ":" + currency → 잔액. */
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();

    @Override
    public void verifyAccountPassword(Long userId, String accountPassword) {
        if (!DEV_PASSWORD.equals(accountPassword)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "계좌 비밀번호가 일치하지 않습니다.");
        }
    }

    @Override
    public synchronized FxLegResult applyFxLegs(Long userId,
                                                String fromCurrency, BigDecimal fromAmount,
                                                String toCurrency, BigDecimal toAmount,
                                                Long fxTransactionId) {
        log.warn("[DEV STUB] CmaFundsPort — 실제 CMA 어댑터로 교체 필요. fxTxId={} {} {} → {} {}",
                fxTransactionId, fromAmount, fromCurrency, toAmount, toCurrency);

        BigDecimal from = balance(userId, fromCurrency);
        if (from.compareTo(fromAmount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    fromCurrency + " 풀 잔액이 부족합니다. (보유 " + from + ", 필요 " + fromAmount + ")");
        }
        BigDecimal remainFrom = from.subtract(fromAmount);
        BigDecimal remainTo = balance(userId, toCurrency).add(toAmount);
        balances.put(key(userId, fromCurrency), remainFrom);
        balances.put(key(userId, toCurrency), remainTo);
        return new FxLegResult(remainFrom, remainTo);
    }

    private BigDecimal balance(Long userId, String currency) {
        return balances.computeIfAbsent(key(userId, currency),
                k -> "USD".equals(currency) ? SEED_USD : SEED_KRW);
    }

    private String key(Long userId, String currency) {
        return userId + ":" + currency;
    }
}
