package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
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
import com.pocketstock.ledger.cma.support.CmaAccountNoCipher;
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
    // 포인트 수집 소스 표시명 — 신한 포인트는 단독, 그 외 제휴 포인트는 하나로 묶는다.
    private static final String POINT_SHINHAN_NAME = "마이신한포인트";
    private static final String POINT_AFFILIATE_NAME = "제휴 포인트";
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
    private final CmaAccountNoCipher cipher;

    @Transactional(readOnly = true)
    public CmaHomeResponse getHome(Long userId) {
        requireUser(userId);
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
        PointBreakdown points    = calcPointBreakdown(userId, settings);   // 신한 / 제휴 분리
        List<CmaHomeResponse.CollectSource> fxSources = calcFxSources(userId);

        BigDecimal pointAmount = points.shinhan().add(points.affiliate());
        BigDecimal totalCollectable = accountAmount.add(pointAmount);   // KRW 소스 합
        BigDecimal totalCollectableUsd = fxSources.stream()            // USD 소스 합(통화가 달라 KRW와 분리)
                .map(CmaHomeResponse.CollectSource::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 화면 노출 순서: 마이신한포인트 → 제휴 포인트 → SOL트래블 외화예금(FX) → 신한은행(끝전)
        // 포인트는 잔액이 있는 그룹만 노출(빈 줄 방지). 수집 실행은 sourceType=POINT로 둘 다 함께 처리된다.
        List<CmaHomeResponse.CollectSource> collectSources = new ArrayList<>();
        if (points.shinhan().signum() > 0) {
            collectSources.add(new CmaHomeResponse.CollectSource("POINT", POINT_SHINHAN_NAME, points.shinhan(), KRW));
        }
        if (points.affiliate().signum() > 0) {
            collectSources.add(new CmaHomeResponse.CollectSource("POINT", POINT_AFFILIATE_NAME, points.affiliate(), KRW));
        }
        collectSources.addAll(fxSources);
        collectSources.add(new CmaHomeResponse.CollectSource("ACCOUNT", SOURCE_NAMES.get("ACCOUNT"), accountAmount, KRW));

        String cmaAccountNo = cipher.decrypt(account.getAccountNoEnc());

        return new CmaHomeResponse(
                cmaAccountNo,
                cmaBalance, interestRate, todayInterest,
                collectedSources, collectSources, totalCollectable, totalCollectableUsd
        );
    }

    @Transactional(readOnly = true)
    public List<CmaTransactionResponse> getTransactions(
            Long userId, String txType, LocalDate from, LocalDate to, int page, int size) {
        requireUser(userId);
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
        requireUser(userId);
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
        requireUser(userId);
        return transactionMapper.findCollectHistory(userId, page * size, size)
                .stream()
                .map(CmaTransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CmaTransactionResponse> getTransfers(Long userId, int page, int size) {
        requireUser(userId);
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
        // 실제 수집(collectFromFx)과 동일하게 잔액 있는(>0) 지갑만 노출 — 빈 지갑이 "수집 가능"에 뜨지 않게 한다.
        return assetFeignClient.getUsdWallets(userId).stream()
                .filter(w -> w.balance() != null && w.balance().signum() > 0)
                .map(w -> new CmaHomeResponse.CollectSource("FX", w.accountName(), w.balance(), USD))
                .toList();
    }

    /**
     * 활성 POINT 소스를 신한(마이신한포인트) / 제휴(그 외 전부)로 나눠 각각 합산한다.
     * 화면은 두 줄(마이신한포인트, 제휴 포인트)로 보여주고, 수집 실행은 기존대로 POINT 전체를 함께 처리한다.
     */
    private PointBreakdown calcPointBreakdown(Long userId, List<CollectionSetting> settings) {
        BigDecimal shinhan = BigDecimal.ZERO;
        BigDecimal affiliate = BigDecimal.ZERO;
        for (CollectionSetting s : settings) {
            if (!"POINT".equals(s.getSourceType()) || !Boolean.TRUE.equals(s.getIsEnabled())) {
                continue;
            }
            PointSummary p = assetFeignClient.getAvailablePoints(userId, s.getSourceRefId());
            BigDecimal amount = p.availablePoints();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            if (isShinhanPoint(p.pointName())) {
                shinhan = shinhan.add(amount);
            } else {
                affiliate = affiliate.add(amount);
            }
        }
        return new PointBreakdown(shinhan, affiliate);
    }

    /** 포인트명에 "신한"이 들어가면 신한 포인트(마이신한포인트)로 분류, 그 외는 제휴. */
    private boolean isShinhanPoint(String pointName) {
        return pointName != null && pointName.contains("신한");
    }

    private record PointBreakdown(BigDecimal shinhan, BigDecimal affiliate) {}

    /**
     * 미인증(토큰 없음/만료/무효) 요청은 401로 차단 — 다른 CMA 서비스(requireUser)와 동일 컨벤션.
     * 이 가드가 없으면 userId=null이 findByUserId까지 내려가 404가 되어, 프론트가 인증 실패를 "CMA 미보유(신규회원)"로 오독한다.
     * 인증됐지만 계좌가 없는 진짜 신규회원은 userId가 살아있어 여기를 통과하고 계좌 조회 단계에서 404로 분기된다.
     */
    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }
}
