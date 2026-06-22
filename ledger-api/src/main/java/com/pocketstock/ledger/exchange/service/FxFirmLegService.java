package com.pocketstock.ledger.exchange.service;

import com.pocketstock.ledger.exchange.domain.OperatingFxPnl;
import com.pocketstock.ledger.exchange.mapper.OperatingFxPnlMapper;
import com.pocketstock.ledger.firm.service.OperatingCashService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 환전의 <b>회사쪽 복식부기 leg</b> — 고객 CMA 양다리({@code CmaFundsPort})의 상대계정(H5 #96).
 * 호출자({@link ExchangeSettleService})의 {@code @Transactional} 안에서 함께 커밋된다.
 *
 * <p>고객이 {@code from}을 내고 {@code to}를 받으면, 그 반대편(회사 통화풀)은 {@code from}을 받고
 * {@code to}를 내준다 — 통화풀 2-leg으로 <b>통화별 보존(Σ=0)</b>을 만든다(operating_cash_*):
 * <pre>
 *   firm operating_cash[from] += fromAmount   (회사가 고객이 낸 통화를 수취)
 *   firm operating_cash[to]   -= toAmount      (회사가 고객에게 줄 통화를 지급)
 * </pre>
 * 두 leg을 기준통화(KRW)·매매기준율(mid)로 환산하면 비대칭이 남는데, 그게 회사가 가져간
 * <b>실현 스프레드</b>다. operating_fx_pnl에 1줄로 박제한다(보존과 별개의 P&L 측정치).
 */
@Service
@RequiredArgsConstructor
public class FxFirmLegService {

    private static final String BASE_CURRENCY = "KRW";
    private static final String TX_TYPE_FX = "FX";
    private static final String REF_TYPE_FX = "fx";
    private static final int PNL_SCALE = 4;

    private final OperatingCashService operatingCashService;
    private final OperatingFxPnlMapper fxPnlMapper;

    /**
     * 회사 통화풀 2-leg + 환차익 1줄. 멱등키는 fxTxId 파생키로 재적재 중복을 막는다.
     *
     * @param midRate     매매기준율(손익 측정 기준)
     * @param appliedRate 고객 적용환율(buyRate/sellRate)
     */
    public void record(Long fxTxId, String fromCurrency, BigDecimal fromAmount,
                       String toCurrency, BigDecimal toAmount,
                       BigDecimal midRate, BigDecimal appliedRate) {
        // 통화풀 2-leg — 회사는 고객의 반대편: from 수취(+), to 지급(−).
        operatingCashService.record(TX_TYPE_FX, fromAmount, fromCurrency,
                REF_TYPE_FX, fxTxId, "fx:" + fxTxId + ":" + fromCurrency);
        operatingCashService.record(TX_TYPE_FX, toAmount.negate(), toCurrency,
                REF_TYPE_FX, fxTxId, "fx:" + fxTxId + ":" + toCurrency);

        // 실현 스프레드 = (회사 수취액 − 회사 지급액)을 기준통화(KRW)·mid로 환산.
        BigDecimal received = toBase(fromCurrency, fromAmount, midRate);
        BigDecimal paid = toBase(toCurrency, toAmount, midRate);
        BigDecimal realizedPnl = received.subtract(paid).setScale(PNL_SCALE, RoundingMode.HALF_UP);

        fxPnlMapper.insert(OperatingFxPnl.builder()
                .fxTransactionId(fxTxId)
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .baseCurrency(BASE_CURRENCY)
                .realizedPnl(realizedPnl)
                .midRate(midRate)
                .appliedRate(appliedRate)
                .idempotencyKey("fx:" + fxTxId + ":pnl")
                .build());
    }

    /** 금액을 기준통화(KRW)로 환산 — KRW는 그대로, 외화는 매매기준율(mid)을 곱한다. */
    private BigDecimal toBase(String currency, BigDecimal amount, BigDecimal midRate) {
        return BASE_CURRENCY.equals(currency) ? amount : amount.multiply(midRate);
    }
}
