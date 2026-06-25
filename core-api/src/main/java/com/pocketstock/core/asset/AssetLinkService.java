package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.asset.domain.LinkedBankAccountInsert;
import com.pocketstock.core.asset.domain.LinkedInstitution;
import com.pocketstock.core.asset.dto.BankLinkResponse;
import com.pocketstock.core.asset.dto.CardLinkResponse;
import com.pocketstock.core.asset.dto.FxLinkResponse;
import com.pocketstock.core.asset.dto.LinkAuthRequest;
import com.pocketstock.core.asset.dto.LinkAuthResponse;
import com.pocketstock.core.asset.dto.LinkRequest;
import com.pocketstock.core.asset.dto.LinkResponse;
import com.pocketstock.core.asset.dto.LinkedRef;
import com.pocketstock.core.asset.dto.MasterRef;
import com.pocketstock.core.asset.dto.PointLinkResponse;
import com.pocketstock.core.asset.dto.RefreshResponse;
import com.pocketstock.core.asset.dto.SecuritiesLinkResponse;
import com.pocketstock.core.asset.mapper.AssetLinkMapper;
import com.pocketstock.core.client.LedgerFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 자산 연동 실행(쓰기). 신규 사용자가 마이데이터로 외부 기관을 연동하면 `linked_*`에 자산을 적재한다.
 *
 * <p>전부 목데이터(외부 API 미호출). 연동 시 생성되는 자산은 **기관별 고정 시나리오 템플릿**을 해당
 * 사용자로 복제 INSERT한다(임의 값 생성 아님). 통합인증 토큰은 무상태 mock(발급만, 검증/저장 안 함).
 * 멱등: 이미 `LINKED`인 기관을 다시 연동하면 자산 재생성 없이 skip한다.
 */
@Service
@RequiredArgsConstructor
public class AssetLinkService {

    private static final String LINKED = "LINKED";
    private static final String SHINHAN_BANK = "SHINHAN_BANK";   // SOL트래블 외화지갑이 매달리는 기관
    private static final BigDecimal FX_USD_BALANCE = new BigDecimal("120.50");

    private final AssetLinkMapper mapper;
    private final LedgerFeignClient ledgerFeignClient;

    // ── 연동 시 적재할 고정 템플릿 (companyCode 기준) ────────────────────────
    private record BankTpl(String accountType, String accountName, BigDecimal balance, String currency) {}
    private record CardTpl(String cardName, String cardType, String maskedNo) {}
    private record PointTpl(String pointName, long balance) {}
    private record HoldingTpl(String stockCode, String stockName, BigDecimal quantity, BigDecimal evalAmount) {}
    private record SecTpl(BigDecimal depositCash, String currency, List<HoldingTpl> holdings) {}

    private static final List<BankTpl> DEFAULT_BANK =
            List.of(new BankTpl("DEMAND", "입출금 통장", new BigDecimal("500000"), "KRW"));
    private static final PointTpl DEFAULT_POINT = new PointTpl("연동 포인트", 5000);
    private static final SecTpl DEFAULT_SEC = new SecTpl(new BigDecimal("10000"), "KRW", List.of());

    /** 끝전(1,253,400 % 10,000 = 3,400)이 남도록 잔액 구성 — 연동 후 잔돈 스캔 데모 정합. */
    private static final Map<String, List<BankTpl>> BANK_TPL = Map.of(
            "SHINHAN_BANK", List.of(new BankTpl("DEMAND", "신한 주거래 통장", new BigDecimal("1253400"), "KRW")),
            "KB_BANK",      List.of(new BankTpl("DEMAND", "KB 마이핏 통장", new BigDecimal("642800"), "KRW")));

    private static final Map<String, List<CardTpl>> CARD_TPL = Map.of(
            "SHINHAN_CARD", List.of(new CardTpl("신한 SOL 체크카드", "CHECK", "4321-****-****-8765")),
            "KB_CARD",      List.of(new CardTpl("KB 굿데이 카드", "CREDIT", "5310-****-****-1209")));

    private static final Map<String, PointTpl> POINT_TPL = Map.of(
            "SHINHAN_POINT", new PointTpl("마이신한포인트", 15000));

    private static final Map<String, SecTpl> SEC_TPL = Map.of(
            "TOSS_SEC", new SecTpl(new BigDecimal("50000"), "KRW",
                    List.of(new HoldingTpl("005930", "삼성전자", new BigDecimal("1.0000"), new BigDecimal("71000")))));

    // ── 통합인증(무상태 mock) ────────────────────────────────────────────
    public LinkAuthResponse authenticate(LinkAuthRequest request) {
        // 토큰만 발급하고 저장하지 않는다. institutions는 화면 흐름상 받지만 검증에 쓰지 않는다.
        return new LinkAuthResponse("MYD-AUTH-" + UUID.randomUUID());
    }

    // ── 일괄 연동 ────────────────────────────────────────────────────────
    @Transactional
    public LinkResponse linkBulk(Long userId, LinkRequest request) {
        Set<String> codes = new LinkedHashSet<>(request.institutions());   // 중복 제거(멱등)
        int linkedCount = 0;
        for (String code : codes) {
            if (linkOne(userId, code, null).newlyLinked()) {
                linkedCount++;
            }
        }
        return new LinkResponse(linkedCount);
    }

    // ── 개별 연동 ────────────────────────────────────────────────────────
    @Transactional
    public BankLinkResponse linkBank(Long userId, String companyCode) {
        LinkResult r = linkOne(userId, companyCode, "BANK");
        BankLinkResponse rep = mapper.findRepresentativeAccount(userId, r.institutionId());
        return rep != null ? rep : new BankLinkResponse(null, BigDecimal.ZERO);
    }

    @Transactional
    public CardLinkResponse linkCard(Long userId, String companyCode) {
        LinkResult r = linkOne(userId, companyCode, "CARD");
        return new CardLinkResponse(true, mapper.countCards(userId, r.institutionId()));
    }

    @Transactional
    public PointLinkResponse linkPoint(Long userId, String companyCode) {
        LinkResult r = linkOne(userId, companyCode, "POINT");
        Long balance = mapper.findPointBalance(userId, r.institutionId());
        return new PointLinkResponse(companyCode, balance != null ? balance : 0L);
    }

    @Transactional
    public SecuritiesLinkResponse linkSecurities(Long userId, String companyCode) {
        LinkResult r = linkOne(userId, companyCode, "SECURITIES");
        return new SecuritiesLinkResponse(true, mapper.countHoldings(userId, r.institutionId()));
    }

    /**
     * SOL트래블 외화잔액 연동 — SHINHAN_BANK 커넥션에 USD 지갑을 적재한다(원화 계좌 템플릿과 독립).
     * 멱등: 이미 USD 지갑이 있으면 그대로 사용. krwEquivalent는 환율 미수신 시 null.
     */
    @Transactional
    public FxLinkResponse linkFx(Long userId) {
        Long institutionId = ensureInstitution(userId, SHINHAN_BANK);

        BigDecimal usd = mapper.findUsdWalletBalance(userId, institutionId);
        if (usd == null) {
            LinkedBankAccountInsert wallet = new LinkedBankAccountInsert();
            wallet.setUserId(userId);
            wallet.setInstitutionId(institutionId);
            wallet.setAccountType("DEMAND");
            wallet.setAccountName("SOL트래블 외화예금");
            wallet.setBalance(FX_USD_BALANCE);
            wallet.setCurrency("USD");
            mapper.insertBankAccount(wallet);
            usd = FX_USD_BALANCE;
        }
        return new FxLinkResponse(usd, toKrwBestEffort(usd));
    }

    // ── 새로고침(no-op) ──────────────────────────────────────────────────
    @Transactional
    public RefreshResponse refresh(Long userId) {
        mapper.touchLastSynced(userId);
        // 응답·저장값 불일치(DB NOW() vs 앱 시각·타임존) 방지 — DB가 찍은 값을 그대로 읽어 반환.
        LocalDateTime syncedAt = mapper.findMaxLastSyncedAt(userId);
        return new RefreshResponse(syncedAt != null ? syncedAt : LocalDateTime.now());  // 연동 기관 없으면 폴백
    }

    // ── 내부 ────────────────────────────────────────────────────────────
    private record LinkResult(Long institutionId, String category, boolean newlyLinked) {}

    /**
     * companyCode 기관을 연동(커넥션 생성/승격 + 템플릿 자산 적재). 이미 LINKED면 자산 적재 없이 skip.
     * {@code expectedCategory}가 주어지면 카테고리 불일치를 쓰기 전에 400으로 막는다.
     */
    private LinkResult linkOne(Long userId, String companyCode, String expectedCategory) {
        if (companyCode == null || companyCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "기관 코드가 필요합니다.");
        }
        MasterRef master = mapper.findMasterByCode(companyCode);
        if (master == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 기관입니다: " + companyCode);
        }
        if (expectedCategory != null && !expectedCategory.equals(master.category())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    companyCode + "은(는) " + expectedCategory + " 연동 대상이 아닙니다.");
        }

        LinkedRef existing = mapper.findLinkedInstitution(userId, master.id());
        Long institutionId;
        boolean newly;
        if (existing == null) {
            LinkedInstitution li = new LinkedInstitution();
            li.setUserId(userId);
            li.setInstitutionMasterId(master.id());
            li.setLinkStatus(LINKED);
            mapper.insertLinkedInstitution(li);
            institutionId = li.getId();
            newly = true;
        } else if (!LINKED.equals(existing.linkStatus())) {
            mapper.markLinked(existing.id());
            institutionId = existing.id();
            newly = true;
        } else {
            institutionId = existing.id();
            newly = false;       // 멱등 skip
        }

        if (newly) {
            insertTemplateAssets(userId, institutionId, master.category(), companyCode);
        }
        return new LinkResult(institutionId, master.category(), newly);
    }

    /** SHINHAN_BANK 등 커넥션을 보장(없으면 LINKED 생성, 자산 적재 없음) — FX 전용. */
    private Long ensureInstitution(Long userId, String companyCode) {
        MasterRef master = mapper.findMasterByCode(companyCode);
        if (master == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 기관입니다: " + companyCode);
        }
        LinkedRef existing = mapper.findLinkedInstitution(userId, master.id());
        if (existing == null) {
            LinkedInstitution li = new LinkedInstitution();
            li.setUserId(userId);
            li.setInstitutionMasterId(master.id());
            li.setLinkStatus(LINKED);
            mapper.insertLinkedInstitution(li);
            return li.getId();
        }
        if (!LINKED.equals(existing.linkStatus())) {
            mapper.markLinked(existing.id());
        }
        return existing.id();
    }

    private void insertTemplateAssets(Long userId, Long institutionId, String category, String companyCode) {
        switch (category) {
            case "BANK" -> {
                for (BankTpl t : BANK_TPL.getOrDefault(companyCode, DEFAULT_BANK)) {
                    LinkedBankAccountInsert a = new LinkedBankAccountInsert();
                    a.setUserId(userId);
                    a.setInstitutionId(institutionId);
                    a.setAccountType(t.accountType());
                    a.setAccountName(t.accountName());
                    a.setBalance(t.balance());
                    a.setCurrency(t.currency());
                    mapper.insertBankAccount(a);
                }
            }
            case "CARD" -> {
                List<CardTpl> tpls = CARD_TPL.get(companyCode);
                if (tpls != null) {
                    for (CardTpl t : tpls) {
                        mapper.insertCard(userId, institutionId, t.cardName(), t.cardType(), t.maskedNo());
                    }
                }
            }
            case "POINT" -> {
                PointTpl t = POINT_TPL.getOrDefault(companyCode, DEFAULT_POINT);
                mapper.insertPoint(userId, institutionId, t.pointName(), t.balance());
            }
            case "SECURITIES" -> {
                SecTpl t = SEC_TPL.getOrDefault(companyCode, DEFAULT_SEC);
                mapper.insertSecurities(userId, institutionId, t.depositCash(), t.currency());
                for (HoldingTpl h : t.holdings()) {
                    mapper.insertHolding(userId, institutionId,
                            h.stockCode(), h.stockName(), h.quantity(), h.evalAmount());
                }
            }
            default -> { /* 알 수 없는 카테고리: 커넥션만 두고 자산 미적재 */ }
        }
    }

    /** USD×매매기준율(정수). 환율 피드 콜드스타트 등으로 실패하면 null(외화지갑 적재는 영향 없음). */
    private BigDecimal toKrwBestEffort(BigDecimal usd) {
        try {
            BigDecimal rate = ledgerFeignClient.getUsdKrwRate().baseRate();
            return usd.multiply(rate).setScale(0, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }
}
