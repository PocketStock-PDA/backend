package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.CardRoundupSummary;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.client.dto.PointSummary;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CmaBalance;
import com.pocketstock.ledger.cma.domain.CollectionSetting;
import com.pocketstock.ledger.cma.dto.response.CmaHomeResponse;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CmaQueryService {

    private static final Map<String, String> SOURCE_NAMES = Map.of(
            "ACCOUNT", "신한은행",
            "CARD",    "SOL트래블",
            "POINT",   "마이신한포인트"
    );

    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;
    private final CmaTransactionMapper transactionMapper;
    private final CollectionSettingMapper settingMapper;
    private final AssetFeignClient assetFeignClient;

    @Transactional(readOnly = true)
    public CmaHomeResponse getHome(Long userId) {
        CmaAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌를 찾을 수 없습니다.");
        }

        List<CmaBalance> balances = balanceMapper.findByAccountId(account.getId());
        Map<String, BigDecimal> cmaBalance = balances.stream()
                .collect(Collectors.toMap(CmaBalance::getCurrency, CmaBalance::getBalance));

        CmaBalance krw = balances.stream()
                .filter(b -> "KRW".equals(b.getCurrency()))
                .findFirst().orElse(null);

        BigDecimal interestRate = krw != null && krw.getInterestRate() != null
                ? krw.getInterestRate() : BigDecimal.ZERO;
        BigDecimal todayInterest = krw != null
                ? krw.getBalance().multiply(interestRate)
                        .divide(BigDecimal.valueOf(365), 0, RoundingMode.DOWN)
                : BigDecimal.ZERO;

        BigDecimal collectedToday = transactionMapper.sumCollectedToday(userId);
        if (collectedToday == null) collectedToday = BigDecimal.ZERO;

        List<CollectionSetting> settings = settingMapper.findByUserId(userId);

        BigDecimal accountAmount = calcAccountAmount(userId, settings);
        BigDecimal cardAmount    = calcCardAmount(userId, settings);
        BigDecimal pointAmount   = calcPointAmount(userId, settings);
        BigDecimal totalCollectable = accountAmount.add(cardAmount).add(pointAmount);

        List<CmaHomeResponse.CollectSource> collectSources = List.of(
                new CmaHomeResponse.CollectSource("ACCOUNT", SOURCE_NAMES.get("ACCOUNT"), accountAmount),
                new CmaHomeResponse.CollectSource("CARD",    SOURCE_NAMES.get("CARD"),    cardAmount),
                new CmaHomeResponse.CollectSource("POINT",   SOURCE_NAMES.get("POINT"),   pointAmount)
        );

        return new CmaHomeResponse(
                cmaBalance, interestRate, todayInterest,
                collectedToday, collectSources, totalCollectable
        );
    }

    private BigDecimal calcAccountAmount(Long userId, List<CollectionSetting> settings) {
        List<Long> enabledIds = settings.stream()
                .filter(s -> "ACCOUNT".equals(s.getSourceType()) && Boolean.TRUE.equals(s.getIsEnabled()))
                .map(CollectionSetting::getSourceRefId)
                .toList();
        if (enabledIds.isEmpty()) return BigDecimal.ZERO;

        List<LinkedAccountSummary> accounts = assetFeignClient.getLinkedAccounts(userId, enabledIds);
        return accounts.stream()
                .map(a -> a.balance().remainder(BigDecimal.valueOf(10_000)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calcCardAmount(Long userId, List<CollectionSetting> settings) {
        return settings.stream()
                .filter(s -> "CARD".equals(s.getSourceType()) && Boolean.TRUE.equals(s.getIsEnabled()))
                .findFirst()
                .map(s -> {
                    CardRoundupSummary roundup = assetFeignClient.getCardRoundup(userId, s.getSourceRefId());
                    return roundup.totalRoundupAmount();
                })
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calcPointAmount(Long userId, List<CollectionSetting> settings) {
        return settings.stream()
                .filter(s -> "POINT".equals(s.getSourceType()) && Boolean.TRUE.equals(s.getIsEnabled()))
                .findFirst()
                .map(s -> {
                    PointSummary point = assetFeignClient.getAvailablePoints(userId, s.getSourceRefId());
                    return point.availablePoints();
                })
                .orElse(BigDecimal.ZERO);
    }
}
