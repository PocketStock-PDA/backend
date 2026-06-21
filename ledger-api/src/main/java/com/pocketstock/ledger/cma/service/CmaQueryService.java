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
import com.pocketstock.ledger.cma.dto.response.CmaBalanceResponse;
import com.pocketstock.ledger.cma.dto.response.CmaHomeResponse;
import com.pocketstock.ledger.cma.dto.response.CmaTransactionResponse;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CmaBalanceMapper;
import com.pocketstock.ledger.cma.mapper.CmaTransactionMapper;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import com.pocketstock.ledger.exchange.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.valueOf(10000);
    private static final String KRW = "KRW";
    private static final String USD = "USD";

    private final CmaAccountMapper accountMapper;
    private final CmaBalanceMapper balanceMapper;
    private final CmaTransactionMapper transactionMapper;
    private final CollectionSettingMapper settingMapper;
    private final AssetFeignClient assetFeignClient;
    private final ExchangeRateService exchangeRateService;

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

    @Transactional(readOnly = true)
    public List<CmaTransactionResponse> getTransactions(
            Long userId, String txType, LocalDate from, LocalDate to, int page, int size) {
        LocalDateTime fromDt = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime toDt   = (to   != null) ? to.plusDays(1).atStartOfDay() : null;

        return transactionMapper.findByUserIdAndFilter(
                        userId, txType, null, fromDt, toDt, page * size, size)
                .stream()
                .map(CmaTransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CmaBalanceResponse getBalance(Long userId) {
        CmaAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌를 찾을 수 없습니다.");
        }

        List<CmaBalance> balances = balanceMapper.findByAccountId(account.getId());

        List<CmaBalanceResponse.BalanceItem> items = balances.stream()
                .map(b -> new CmaBalanceResponse.BalanceItem(
                        b.getCurrency(),
                        b.getBalance(),
                        b.getInterestRate() != null ? b.getInterestRate() : BigDecimal.ZERO,
                        "KRW".equals(b.getCurrency()) ? "KRW_RP" : "USD_RP"
                ))
                .toList();

        return new CmaBalanceResponse(items, calcTotalKrwEquivalent(balances));
    }

    /**
     * 총 평가액(KRW 환산) = KRW 잔액 + USD 잔액 × 매매기준율.
     * USD 미보유(행 없음/0)면 환율을 조회하지 않아 KRW 전용 계좌는 항상 성공한다.
     * USD>0 인데 환율 캐시가 비어 있으면(콜드스타트) 환전 API와 동일하게 502가 전파되어,
     * 틀린(과소) 총액을 노출하지 않는다.
     */
    private BigDecimal calcTotalKrwEquivalent(List<CmaBalance> balances) {
        BigDecimal totalKrw = sumByCurrency(balances, KRW);
        BigDecimal usd = sumByCurrency(balances, USD);
        if (usd.signum() == 0) {
            return totalKrw;
        }
        BigDecimal baseRate = exchangeRateService.getUsdKrwRate().baseRate();
        BigDecimal usdInKrw = usd.multiply(baseRate).setScale(0, RoundingMode.HALF_UP);
        return totalKrw.add(usdInKrw);
    }

    private BigDecimal sumByCurrency(List<CmaBalance> balances, String currency) {
        return balances.stream()
                .filter(b -> currency.equals(b.getCurrency()))
                .map(CmaBalance::getBalance)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<CmaTransactionResponse> getCollectHistory(Long userId, int page, int size) {
        return transactionMapper.findCollectHistory(userId, page * size, size)
                .stream()
                .map(CmaTransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CmaTransactionResponse> getTransfers(Long userId, int page, int size) {
        return transactionMapper.findTransfers(userId, page * size, size)
                .stream()
                .map(CmaTransactionResponse::from)
                .toList();
    }

    private BigDecimal calcAccountAmount(Long userId, List<CollectionSetting> settings) {
        List<CollectionSetting> enabledSettings = settings.stream()
                .filter(s -> "ACCOUNT".equals(s.getSourceType()) && Boolean.TRUE.equals(s.getIsEnabled()))
                .toList();
        if (enabledSettings.isEmpty()) return BigDecimal.ZERO;

        List<Long> enabledIds = enabledSettings.stream()
                .map(CollectionSetting::getSourceRefId)
                .toList();

        List<LinkedAccountSummary> accounts = assetFeignClient.getLinkedAccounts(userId, enabledIds);

        // 계좌별로 해당 설정의 threshold를 적용해 끝전 계산
        Map<Long, BigDecimal> thresholdByRefId = enabledSettings.stream()
                .collect(Collectors.toMap(
                        CollectionSetting::getSourceRefId,
                        s -> s.getThreshold() != null ? s.getThreshold() : DEFAULT_THRESHOLD
                ));

        return accounts.stream()
                .map(a -> {
                    BigDecimal threshold = thresholdByRefId.getOrDefault(a.id(), DEFAULT_THRESHOLD);
                    return a.balance().remainder(threshold);
                })
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
