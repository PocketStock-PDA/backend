package com.pocketstock.ledger.trading.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.trading.domain.Holding;
import com.pocketstock.ledger.trading.domain.WholeShareEvent;
import com.pocketstock.ledger.trading.dto.WholeShareConvertResponse;
import com.pocketstock.ledger.trading.dto.WholeShareHistoryResponse;
import com.pocketstock.ledger.trading.mapper.HoldingMapper;
import com.pocketstock.ledger.trading.mapper.WholeShareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 온주 전환 — 사용자가 버튼을 누르면 그 종목의 소수점(신탁) 보유 정수부를 온주(직접소유)로 굳힌다(FRAC-010 #157).
 * 전환분은 정수 매도만, 남은 소수만 소수 매도 가능(온주→소수 역전환 불가). holdings.quantity는 불변 —
 * 온주(=quantity−fractional_qty)가 wholeQty만큼 늘고 fractional_qty가 그만큼 줄 뿐. 전환내역은 whole_share_events 적재.
 */
@Service
@RequiredArgsConstructor
public class WholeShareService {

    private final HoldingMapper holdingMapper;
    private final WholeShareMapper wholeShareMapper;

    /** 온주 전환 — 미체결 매도분(held) 제외한 소수의 정수부를 온주로 전환. 1주 미만이면 거부. */
    @Transactional
    public WholeShareConvertResponse convert(Long userId, String stockCode) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (stockCode == null || stockCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종목코드가 필요합니다.");
        }
        Holding h = holdingMapper.findByUserIdAndStock(userId, stockCode.trim());
        if (h == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "보유하지 않은 종목입니다: " + stockCode);
        }
        BigDecimal fractional = h.getFractionalQty() == null ? BigDecimal.ZERO : h.getFractionalQty();
        BigDecimal held = h.getHeldFractional() == null ? BigDecimal.ZERO : h.getHeldFractional();
        // 전환 가능 소수 = 소수보유 − 미체결 매도분(held). 그 정수부만 온주로 굳힌다.
        int wholeQty = fractional.subtract(held).setScale(0, RoundingMode.FLOOR).max(BigDecimal.ZERO).intValueExact();
        if (wholeQty < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "전환할 소수점이 1주 미만입니다(전환가능 " + fractional.subtract(held) + "주).");
        }
        // 원자 차감 + 가드(전환 후 소수 − held ≥ 0). 0행이면 그 사이 매수/매도/전환으로 변경됨.
        if (holdingMapper.reduceFractionalForConvert(h.getAccountId(), stockCode.trim(), wholeQty) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "전환 가능 수량이 변경되었습니다. 다시 시도해주세요.");
        }
        wholeShareMapper.insert(WholeShareEvent.builder()
                .userId(userId)
                .accountId(h.getAccountId())
                .stockCode(stockCode.trim())
                .wholeQty(wholeQty)
                .triggeredAt(LocalDateTime.now())
                .build());

        BigDecimal remainingFractional = fractional.subtract(BigDecimal.valueOf(wholeQty));
        BigDecimal totalQuantity = h.getQuantity();
        BigDecimal wholeTotal = totalQuantity.subtract(remainingFractional);   // 전환 후 직접소유 온주 합
        return new WholeShareConvertResponse(stockCode.trim(), wholeQty,
                remainingFractional, wholeTotal, totalQuantity);
    }

    /** 온주 전환내역(최신순). */
    @Transactional(readOnly = true)
    public List<WholeShareHistoryResponse> getHistory(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return wholeShareMapper.findByUserId(userId).stream()
                .map(e -> new WholeShareHistoryResponse(e.getStockCode(), e.getStockName(),
                        e.getWholeQty(), e.getTriggeredAt()))
                .toList();
    }
}
