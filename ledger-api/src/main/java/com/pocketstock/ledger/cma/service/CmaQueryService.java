package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CmaQueryService {

    // 수집 가능 잔돈의 원화 소스 표시명. 외화(FX) 소스명은 실제 계좌명(account_name)을 그대로 쓴다.
    private static final Map<String, String> SOURCE_NAMES = Map.of(
            "ACCOUNT", "신한은행",
            "POINT",   "마이신한포인트"
    );
    // "수집한 잔돈" 영역은 카드(CARD)만 노출 — 계좌 끝전/포인트 전환은 응답에서 제외(확정).
    private static final String COLLECTED_CARD_NAME = "카드 사용 잔돈";
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

        // "수집한 잔돈" 영역 — 이번 달 카드 라운드업 수집액만 노출(수집 0건이면 빈 리스트).
        BigDecimal cardCollected = transactionMapper.sumCardCollectedThisMonth(userId);
        List<CmaHomeResponse.CollectSource> collectedSources =
                (cardCollected != null && cardCollected.signum() > 0)
                        ? List.of(new CmaHomeResponse.CollectSource("CARD", COLLECTED_CARD_NAME, cardCollected, KRW))
                        : List.of();

        List<CollectionSetting> settings = settingMapper.findByUserId(userId);

        // "수집 가능 잔돈" — 원화 소스(계좌 끝전·포인트)와 외화 소스(FX)를 통화별로 분리한다.
        // 끝전(ACCOUNT)은 원화 전용 개념이라 외화는 끼지 않고, 외화 입출금 지갑은 전부 FX로 노출한다.
        BigDecimal accountAmount = calcAccountAmount(userId, settings);
        BigDecimal pointAmount   = calcPointAmount(userId, settings);
        List<CmaHomeResponse.CollectSource> fxSources = calcFxSources(userId);

        BigDecimal totalCollectable = accountAmount.add(pointAmount);   // KRW 소스 합
        BigDecimal totalCollectableUsd = fxSources.stream()            // USD 소스 합(통화가 달라 KRW와 분리)
                .map(CmaHomeResponse.CollectSource::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 화면 노출 순서: 포인트 → SOL트래블 외화예금(FX) → 신한은행(끝전)
        List<CmaHomeResponse.CollectSource> collectSources = new ArrayList<>();
        collectSources.add(new CmaHomeResponse.CollectSource("POINT", SOURCE_NAMES.get("POINT"), pointAmount, KRW));
        collectSources.addAll(fxSources);
        collectSources.add(new CmaHomeResponse.CollectSource("ACCOUNT", SOURCE_NAMES.get("ACCOUNT"), accountAmount, KRW));

        return new CmaHomeResponse(
                cmaBalance, interestRate, todayInterest,
                collectedSources, collectSources, totalCollectable, totalCollectableUsd
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
                // 끝전은 원화 전용 — 외화 계좌가 ACCOUNT 소스로 설정돼도 제외한다(외화는 FX로 수집).
                .filter(a -> KRW.equals(a.currency()))
                .map(a -> {
                    BigDecimal threshold = thresholdByRefId.getOrDefault(a.id(), DEFAULT_THRESHOLD);
                    return a.balance().remainder(threshold);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 외화(USD) 잔돈 소스 — USD 입출금 지갑을 계좌 단위로 그대로 노출한다(합산해 한 라벨로 묶지 않는다).
     * 줄 이름은 실제 계좌명(account_name)을 쓰고, 금액은 그 계좌의 USD 잔액이다(currency=USD).
     * 현재 모집단은 SOL트래블 외화예금 한 계좌. 다은행 외화지갑 분리 표시는 후속(고도화).
     */
    private List<CmaHomeResponse.CollectSource> calcFxSources(Long userId) {
        return assetFeignClient.getUsdWallets(userId).stream()
                .map(w -> new CmaHomeResponse.CollectSource("FX", w.accountName(), w.balance(), USD))
                .toList();
    }

    private BigDecimal calcPointAmount(Long userId, List<CollectionSetting> settings) {
        // 다중 포인트 합산 — 수집 실행(CmaCollectService.collectFromPoint)과 동일하게 활성 POINT 전부 더한다.
        return settings.stream()
                .filter(s -> "POINT".equals(s.getSourceType()) && Boolean.TRUE.equals(s.getIsEnabled()))
                .map(s -> assetFeignClient.getAvailablePoints(userId, s.getSourceRefId()).availablePoints())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
