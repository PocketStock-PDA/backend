package com.pocketstock.core.mypage;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.budget.dto.BudgetSavingsRow;
import com.pocketstock.core.budget.mapper.BudgetSavingsMapper;
import com.pocketstock.core.client.LedgerFeignClient;
import com.pocketstock.core.client.dto.CollectionSettingView;
import com.pocketstock.core.mypage.dto.LinkedInstitutionRow;
import com.pocketstock.core.mypage.dto.MyPageSettings;
import com.pocketstock.core.mypage.dto.MyProfileResponse;
import com.pocketstock.core.mypage.dto.UpdateMyPageSettingsRequest;
import com.pocketstock.core.mypage.mapper.MyPageMapper;
import com.pocketstock.user.member.domain.Member;
import com.pocketstock.user.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 마이페이지 집계 조회 + 설정 토글.
 * <p>email은 members에 컬럼이 없어 응답에서 제외한다.
 * 데이터 출처: name/username·연동기관·절약금토글 = core DB A(로컬), CMA잔액·카드토글·퍼즐판평가 = ledger(Feign).
 * 퍼즐판 평가액 = ledger 실시간 집계(보유 × 현재가, 포트폴리오 화면과 동일 소스).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyPageService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String SOURCE_TYPE_CARD = "CARD";

    private final MemberMapper memberMapper;
    private final MyPageMapper myPageMapper;
    private final BudgetSavingsMapper budgetSavingsMapper;
    private final LedgerFeignClient ledgerFeignClient;

    @Transactional(readOnly = true)
    public MyProfileResponse getMyPage(Long userId) {
        Member member = memberMapper.findById(userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        BigDecimal cmaBalance = fetchCmaKrwBalance(userId);
        BigDecimal puzzleValuation = fetchPuzzleValuation(userId);

        List<MyProfileResponse.LinkedAccount> linkedAccounts = myPageMapper.findLinkedInstitutions(userId).stream()
                .map(this::toLinkedAccount)
                .toList();

        return new MyProfileResponse(
                member.getName(),
                member.getUsername(),
                cmaBalance,
                puzzleValuation,
                linkedAccounts,
                readSettings(userId)
        );
    }

    /**
     * 변경분만 부분 적용한다. 두 설정은 서로 다른 저장소(절약금=core DB A, 카드=ledger)라
     * 원자적 일괄 적용은 불가능하며, 변경분을 순차 best-effort로 반영한다(부분 적용 허용).
     * 외부(ledger) 호출을 DB 트랜잭션 안에 두지 않도록 메서드를 @Transactional로 감싸지 않고,
     * 로컬 단일 upsert를 먼저 커밋해 이후 ledger 호출 실패가 로컬 변경을 되돌리지 않게 한다.
     */
    public MyPageSettings updateSettings(Long userId, UpdateMyPageSettingsRequest request) {
        if (request.cardChangeCollect() == null && request.monthlySavingCollect() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 1) 로컬 변경 먼저(단일 upsert, 자체 원자성)
        if (request.monthlySavingCollect() != null) {
            budgetSavingsMapper.setCollectAgreed(userId, currentPeriod(), request.monthlySavingCollect());
        }
        // 2) 외부 ledger 변경(카드 잔돈 모으기 마스터 토글 → collection_settings(CARD) 일괄 동기화)
        if (request.cardChangeCollect() != null) {
            ledgerFeignClient.updateCollectionEnabled(userId, SOURCE_TYPE_CARD, request.cardChangeCollect());
        }

        return readSettings(userId);
    }

    /** 토글 상태 조합: cardChangeCollect=ledger의 CARD 활성 여부, monthlySavingCollect=현재월 동의 여부. */
    private MyPageSettings readSettings(Long userId) {
        boolean cardChangeCollect = ledgerFeignClient.getCollectionSettings(userId).stream()
                .anyMatch(s -> SOURCE_TYPE_CARD.equals(s.sourceType()) && s.enabled());

        BudgetSavingsRow savings = budgetSavingsMapper.findBudgetSavings(userId, currentPeriod());
        boolean monthlySavingCollect = savings != null && savings.isCollectAgreed();

        return new MyPageSettings(cardChangeCollect, monthlySavingCollect);
    }

    /** CMA KRW 잔액 — 미개설/콜드스타트 등 ledger 오류 시 0으로 방어(화면은 0 표시). */
    private BigDecimal fetchCmaKrwBalance(Long userId) {
        try {
            BigDecimal balance = ledgerFeignClient.getCmaKrwBalance(userId);
            return balance != null ? balance : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("CMA KRW 잔액 조회 실패 (userId={}): {}", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** 퍼즐판 실시간 평가액 — ledger 오류/콜드스타트 시 0으로 방어. */
    private BigDecimal fetchPuzzleValuation(Long userId) {
        try {
            BigDecimal valuation = ledgerFeignClient.getPuzzleValuation(userId);
            return valuation != null ? valuation : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("퍼즐판 평가액 조회 실패 (userId={}): {}", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private MyProfileResponse.LinkedAccount toLinkedAccount(LinkedInstitutionRow row) {
        return new MyProfileResponse.LinkedAccount(
                row.companyCode().toLowerCase().replace('_', '-'),
                row.companyName(),
                mapType(row.category()),
                true
        );
    }

    /**
     * 기관 카테고리 → 프론트 아이콘 타입. POINT는 페이류로 PAY 매핑,
     * BANK/CARD/SECURITIES는 그대로. (Figma mock의 TRAVEL은 실제 카테고리에 없어 미사용)
     */
    private String mapType(String category) {
        return "POINT".equals(category) ? "PAY" : category;
    }

    private String currentPeriod() {
        return LocalDate.now(ZoneId.of("UTC")).format(PERIOD_FMT);
    }
}
