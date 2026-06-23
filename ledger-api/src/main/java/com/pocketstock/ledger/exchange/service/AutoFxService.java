package com.pocketstock.ledger.exchange.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.exchange.domain.FxAutoSetting;
import com.pocketstock.ledger.exchange.mapper.FxAutoSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 매수 자동환전 게이트(EXC-005·012, #172) — USD 부족분을 {@code fx_auto_settings} 정책대로 원화 환전해
 * CMA 달러풀을 채운다. trading 매수 충당({@code OrderFundingService})이 USD 부족을 감지하면 호출하고,
 * 호출자 트랜잭션에 합류해 환전+충당+체결이 한 단위로 커밋·롤백된다.
 *
 * <p>정책:
 * <ul>
 *   <li><b>use_dollar_first</b> ON(기본): 보유 달러풀 먼저 쓰고 <b>부족분만</b> 환전 / OFF: <b>필요분 전액</b> 환전
 *       (보유 달러풀은 유지 — 이어진 출금이 환전분만큼만 빼감).</li>
 *   <li><b>is_auto_enabled</b> OFF: 환전이 필요한 순간 거부(매수 실패) — 기존처럼 "달러 있어야만 매수".</li>
 *   <li><b>max_amount_per_tx</b>: 1회 환산 KRW 한도(거액 자동환전 차단, {@link ExchangeSettleService#autoKrwToUsd}에서 검사).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AutoFxService {

    private final FxAutoSettingMapper settingMapper;
    private final ExchangeSettleService settleService;

    /**
     * 매수 충당용 달러 확보 — 부족분만큼(설정대로) 원화 환전. 환전이 불필요하면(달러우선+달러 충분) 아무것도 안 한다.
     *
     * @param shortageUsd   매수에 모자란 USD(예수금 충당 기준)
     * @param dollarPoolBal 현재 CMA 달러풀 잔액
     * @param orderId       연계 매수주문
     */
    public void ensureUsdForOrder(Long userId, BigDecimal shortageUsd, BigDecimal dollarPoolBal, Long orderId) {
        FxAutoSetting s = settingMapper.findByUserId(userId);
        // 미설정 사용자 기본값 = 자동환전 OFF / 달러우선 ON(FxAutoSettingResponse.defaults와 동일).
        boolean autoEnabled = s != null && Boolean.TRUE.equals(s.getIsAutoEnabled());
        boolean dollarFirst = s == null || !Boolean.FALSE.equals(s.getUseDollarFirst());

        // 달러우선=부족분만 환전 / 원화우선=전액 환전(보유 달러풀 안 씀 — 이어진 출금이 환전분만큼만 빼감).
        BigDecimal needFx = dollarFirst ? shortageUsd.subtract(dollarPoolBal).max(BigDecimal.ZERO) : shortageUsd;
        if (needFx.signum() <= 0) {
            return;   // 달러우선 + 달러풀로 충분 → 환전 불필요
        }
        if (!autoEnabled) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "달러가 부족합니다. 자동환전을 켜거나 먼저 환전해주세요.");
        }
        settleService.autoKrwToUsd(userId, needFx, orderId, s == null ? null : s.getMaxAmountPerTx());
    }
}
