package com.pocketstock.ledger.exchange.service;

import com.pocketstock.ledger.exchange.domain.FxAutoSetting;
import com.pocketstock.ledger.exchange.dto.request.FxAutoSettingRequest;
import com.pocketstock.ledger.exchange.dto.response.FxAutoSettingResponse;
import com.pocketstock.ledger.exchange.mapper.FxAutoSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동환전 설정 조회/변경 — 1인 1행 upsert. (환전 자체 도메인이라 CMA 의존 없음)
 */
@Service
@RequiredArgsConstructor
public class FxAutoSettingService {

    private final FxAutoSettingMapper mapper;

    @Transactional(readOnly = true)
    public FxAutoSettingResponse get(Long userId) {
        FxAutoSetting s = mapper.findByUserId(userId);
        return s == null ? FxAutoSettingResponse.defaults() : FxAutoSettingResponse.from(s);
    }

    @Transactional
    public FxAutoSettingResponse update(Long userId, FxAutoSettingRequest req) {
        FxAutoSetting s = new FxAutoSetting();
        s.setUserId(userId);
        s.setIsAutoEnabled(req.autoEnabled() != null ? req.autoEnabled() : Boolean.FALSE);
        s.setUseDollarFirst(req.useDollarFirst() != null ? req.useDollarFirst() : Boolean.TRUE);
        s.setMaxAmountPerTx(req.maxAmountPerTx());
        s.setResidualHandling(req.residualHandling());
        mapper.upsert(s);
        return FxAutoSettingResponse.from(mapper.findByUserId(userId));
    }
}
