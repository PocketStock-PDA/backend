package com.pocketstock.user.member;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.user.member.domain.Term;
import com.pocketstock.user.member.domain.TermsAgreement;
import com.pocketstock.user.member.dto.TermsAgreeRequest;
import com.pocketstock.user.member.mapper.TermsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TermsService {

    private final TermsMapper termsMapper;

    /**
     * 약관 동의 등록.
     * 필수 약관을 거부(agreed=false)하면 동의 자체가 무효이므로 400으로 막고 전체 롤백한다.
     */
    @Transactional
    public void agree(Long userId, TermsAgreeRequest req) {
        if (userId == null) {
            // JwtAuthFilter는 요청을 막지 않으므로(토큰 없으면 attribute 미설정) 여기서 인증을 강제한다.
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
        }
        if (req == null || req.terms() == null || req.terms().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "약관 동의 항목이 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<TermsAgreement> rows = new ArrayList<>();
        Set<Integer> seenTermIds = new HashSet<>();
        Set<Term> agreedTerms = EnumSet.noneOf(Term.class);

        for (TermsAgreeRequest.TermItem item : req.terms()) {
            if (item == null || item.termId() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "약관 항목이 올바르지 않습니다.");
            }
            if (!seenTermIds.add(item.termId())) {                   // 한 요청에 같은 약관 중복 금지
                throw new BusinessException(ErrorCode.INVALID_INPUT, "중복된 약관 항목입니다: " + item.termId());
            }
            Term term = Term.fromId(item.termId());                 // 알 수 없는 termId면 400
            boolean agreed = Boolean.TRUE.equals(item.agreed());

            if (term.isRequired() && !agreed) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "필수 약관에 동의해야 합니다.");
            }
            if (agreed) {
                agreedTerms.add(term);
            }

            rows.add(TermsAgreement.builder()
                    .userId(userId)
                    .termsType(term.getTermsType())
                    .isRequired(term.isRequired())
                    .termsVersion(term.getVersion())
                    .isAgreed(agreed)
                    .agreedAt(now)
                    .build());
        }

        // 필수 약관이 요청에서 누락된 경우도 거부(누락 == 거부) — 전체 롤백
        for (Term term : Term.values()) {
            if (term.isRequired() && !agreedTerms.contains(term)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "필수 약관에 동의해야 합니다.");
            }
        }

        termsMapper.insertAgreements(rows);
    }
}
