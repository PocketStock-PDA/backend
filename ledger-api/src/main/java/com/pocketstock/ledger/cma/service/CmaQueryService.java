package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.dto.response.CmaHomeResponse;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CmaQueryService {

    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;

    @Transactional(readOnly = true)
    public CmaHomeResponse getHome(Long userId) {
        CmaAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌를 찾을 수 없습니다.");
        }

        List<CmaBalance> balances = balanceMapper.findByAccountId(account.getId());
        Map<String, BigDecimal> cmaBalance = balances.stream()
                .collect(Collectors.toMap(CmaBalance::getCurrency, CmaBalance::getBalance));

        // TODO Phase 2: Feign으로 core-api 호출 후 실제 수집 가능 금액 계산
        CmaHomeResponse.CollectableAmount collectableAmount =
                new CmaHomeResponse.CollectableAmount(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        return new CmaHomeResponse(cmaBalance, collectableAmount, BigDecimal.ZERO);
    }
}
