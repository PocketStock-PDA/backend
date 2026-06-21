package com.pocketstock.ledger.cma.service;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.ledger.client.AssetFeignClient;
import com.pocketstock.ledger.client.dto.CardRoundupSummary;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.client.dto.PointSummary;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.domain.CollectionSetting;
import com.pocketstock.ledger.cma.dto.request.CollectionSettingRequest;
import com.pocketstock.ledger.cma.dto.response.CollectResult;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.mapper.CollectionSettingMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 잔돈 수집 실행(쓰기) + 수집 소스 설정.
 *
 * <p>활성 소스의 수집 가능 잔돈을 core-api(자산 도메인)에서 Feign으로 읽어 금액을 계산하고,
 * {@link CmaLedgerWriter}로 CMA 원장(입금 1줄)과 잔액을 기록한다. 소스 타입별 1줄(E-1),
 * {@code ref_type}은 출처 테이블 기준(E-2)으로 남긴다.
 *
 * <p>계산식은 홈 대시보드({@code CmaQueryService.getHome}, 조회)와 동일하다 —
 * 끝전 = 계좌 잔액 % 설정 threshold, 라운드업/포인트는 core-api가 계산해 Feign으로 전달.
 */
@Service
public class CmaCollectService {

    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.valueOf(10000);
    private static final List<BigDecimal> VALID_THRESHOLDS =
            List.of(BigDecimal.valueOf(1000), BigDecimal.valueOf(5000), BigDecimal.valueOf(10000));

    private static final String KRW = "KRW";
    private static final String TX_COLLECT = "COLLECT";
    private static final String SRC_ACCOUNT = "ACCOUNT";
    private static final String SRC_CARD = "CARD";
    private static final String SRC_POINT = "POINT";
    private static final String REF_BANK_ACCOUNT = "LINKED_BANK_ACCOUNT";  // E-2
    private static final String REF_CARD = "LINKED_CARD";
    private static final String REF_POINT = "LINKED_POINT";

    private final CollectionSettingMapper settingMapper;
    private final CmaAccountMapper accountMapper;
    private final CmaLedgerWriter ledgerWriter;
    private final AssetFeignClient assetFeignClient;
    /** 통합 수집이 소스별 @Transactional 메서드를 프록시 경유로 호출하기 위한 자기 참조(셀프인보케이션 회피). */
    private final CmaCollectService self;

    public CmaCollectService(CollectionSettingMapper settingMapper,
                             CmaAccountMapper accountMapper,
                             CmaLedgerWriter ledgerWriter,
                             AssetFeignClient assetFeignClient,
                             @Lazy CmaCollectService self) {
        this.settingMapper = settingMapper;
        this.accountMapper = accountMapper;
        this.ledgerWriter = ledgerWriter;
        this.assetFeignClient = assetFeignClient;
        this.self = self;
    }

    // ── 수집 실행 ─────────────────────────────────────────────────────────

    /** 계좌 끝전 적립 — 활성 ACCOUNT 계좌들의 (잔액 % threshold) 합산. */
    @Transactional
    public CollectResult collectFromAccount(Long userId, String idempotencyKey) {
        CmaAccount account = getAccountOrThrow(userId);
        List<CollectionSetting> enabled = enabledSettings(userId, SRC_ACCOUNT);
        if (enabled.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "활성화된 계좌 적립 소스가 없습니다.");
        }

        List<Long> enabledIds = enabled.stream().map(CollectionSetting::getSourceRefId).toList();
        Map<Long, BigDecimal> thresholdByRefId = enabled.stream().collect(Collectors.toMap(
                CollectionSetting::getSourceRefId,
                s -> s.getThreshold() != null ? s.getThreshold() : DEFAULT_THRESHOLD));

        List<LinkedAccountSummary> accounts = assetFeignClient.getLinkedAccounts(userId, enabledIds);
        BigDecimal amount = accounts.stream()
                .map(a -> a.balance().remainder(thresholdByRefId.getOrDefault(a.id(), DEFAULT_THRESHOLD)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        requireCollectible(amount);

        // E-1: 합산 1줄. ref_id는 계좌 1개면 해당 id, 여러 개면 null(다출처)
        Long refId = enabledIds.size() == 1 ? enabledIds.get(0) : null;
        BigDecimal balanceAfter = ledgerWriter.applyEntry(userId, account.getId(), KRW,
                TX_COLLECT, SRC_ACCOUNT, amount, REF_BANK_ACCOUNT, refId, idempotencyKey);
        return CollectResult.success(SRC_ACCOUNT, amount, balanceAfter);
    }

    /** 카드 라운드업 적립 — 미수집 카드 거래의 천원 올림 차액 합산. 수집 후 core-api에 완료 표시. */
    @Transactional
    public CollectResult collectFromCard(Long userId, String idempotencyKey) {
        CmaAccount account = getAccountOrThrow(userId);
        CollectionSetting setting = enabledSettings(userId, SRC_CARD).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "활성화된 카드 적립 소스가 없습니다."));

        CardRoundupSummary roundup = assetFeignClient.getCardRoundup(userId, setting.getSourceRefId());
        requireCollectible(roundup.totalRoundupAmount());

        BigDecimal balanceAfter = ledgerWriter.applyEntry(userId, account.getId(), KRW,
                TX_COLLECT, SRC_CARD, roundup.totalRoundupAmount(), REF_CARD, setting.getSourceRefId(), idempotencyKey);

        // 수집 완료 표시(core-api, DB A) — 같은 카드 거래가 다시 수집되지 않게 한다(E-3: roundup_amount도 함께 기록).
        // 교차 DB라 한 트랜잭션에 못 묶음 → 원장 기록 후 마지막에 호출(실패 시 본 트랜잭션 롤백되어 재시도 가능).
        assetFeignClient.markRoundupCollected(userId, roundup.cardTransactionIds());
        return CollectResult.success(SRC_CARD, roundup.totalRoundupAmount(), balanceAfter);
    }

    /** 포인트 전환 적립 — 전환 가능 포인트를 CMA 원화 풀로 입금. */
    @Transactional
    public CollectResult collectFromPoint(Long userId, String idempotencyKey) {
        CmaAccount account = getAccountOrThrow(userId);
        CollectionSetting setting = enabledSettings(userId, SRC_POINT).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "활성화된 포인트 적립 소스가 없습니다."));

        PointSummary point = assetFeignClient.getAvailablePoints(userId, setting.getSourceRefId());
        requireCollectible(point.availablePoints());

        BigDecimal balanceAfter = ledgerWriter.applyEntry(userId, account.getId(), KRW,
                TX_COLLECT, SRC_POINT, point.availablePoints(), REF_POINT, setting.getSourceRefId(), idempotencyKey);
        return CollectResult.success(SRC_POINT, point.availablePoints(), balanceAfter);
    }

    /**
     * 통합 수집 — 활성 소스 전체를 독립 실행하고 소스별 결과를 모아 반환(E-1, 부분 성공 허용).
     * 자체 트랜잭션 없음 — 소스별 @Transactional은 {@link #self} 프록시 경유로 적용된다.
     */
    public List<CollectResult> collectAll(Long userId) {
        getAccountOrThrow(userId);   // 계좌 자체가 없으면 전체 실패로 propagate
        List<CollectResult> results = new ArrayList<>();
        results.add(runSource(SRC_ACCOUNT, () -> self.collectFromAccount(userId, newKey())));
        results.add(runSource(SRC_CARD, () -> self.collectFromCard(userId, newKey())));
        results.add(runSource(SRC_POINT, () -> self.collectFromPoint(userId, newKey())));
        return results;
    }

    private CollectResult runSource(String sourceType, Supplier<CollectResult> action) {
        try {
            return action.get();
        } catch (BusinessException e) {        // 소스 비활성·수집 잔돈 없음 = 정상 건너뜀
            return CollectResult.skipped(sourceType, e.getMessage());
        } catch (Exception e) {                // Feign 장애 등 예기치 못한 오류
            return CollectResult.failed(sourceType, e.getMessage());
        }
    }

    // ── 수집 소스 설정 ───────────────────────────────────────────────────

    @Transactional
    public void updateSettings(Long userId, CollectionSettingRequest request) {
        Map<String, CollectionSetting> existingByKey = settingMapper.findByUserId(userId).stream()
                .collect(Collectors.toMap(this::settingKey, Function.identity()));

        for (CollectionSettingRequest.SettingItem item : request.settings()) {
            BigDecimal threshold = item.threshold();
            if (threshold != null && VALID_THRESHOLDS.stream().noneMatch(v -> v.compareTo(threshold) == 0)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "threshold는 1000, 5000, 10000 중 하나여야 합니다.");
            }

            CollectionSetting existing = existingByKey.get(item.sourceType() + ":" + item.sourceRefId());
            BigDecimal resolvedThreshold = threshold != null
                    ? threshold
                    : existing != null && existing.getThreshold() != null
                            ? existing.getThreshold()
                            : DEFAULT_THRESHOLD;

            CollectionSetting setting = new CollectionSetting();
            setting.setUserId(userId);
            setting.setSourceType(item.sourceType());
            setting.setSourceRefId(item.sourceRefId());
            setting.setIsEnabled(item.enabled());
            setting.setThreshold(resolvedThreshold);
            settingMapper.upsert(setting);
        }
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    private List<CollectionSetting> enabledSettings(Long userId, String sourceType) {
        return settingMapper.findByUserId(userId).stream()
                .filter(s -> sourceType.equals(s.getSourceType()) && Boolean.TRUE.equals(s.getIsEnabled()))
                .toList();
    }

    private CmaAccount getAccountOrThrow(Long userId) {
        CmaAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CMA 계좌를 찾을 수 없습니다.");
        }
        return account;
    }

    private void requireCollectible(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수집 가능한 잔돈이 없습니다.");
        }
    }

    private String settingKey(CollectionSetting setting) {
        return setting.getSourceType() + ":" + setting.getSourceRefId();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
