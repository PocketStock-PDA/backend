package com.pocketstock.core.mypage.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 마이페이지 집계 응답(프론트 MyProfile과 1:1).
 * email은 members에 컬럼이 없어 제외. username(로그인 아이디)은 이름 아래 표시용.
 * 금액은 KRW 정수 단위.
 */
public record MyProfileResponse(
        String name,
        String username,
        BigDecimal cmaBalance,
        BigDecimal puzzleValuation,
        List<LinkedAccount> linkedAccounts,
        MyPageSettings settings
) {
    /** 연동된 자산 1건. 연동된 것만 내려주므로 linked는 항상 true. */
    public record LinkedAccount(
            String id,    // company_code 케밥 변환 (SHINHAN_BANK -> shinhan-bank)
            String name,  // company_name (신한은행)
            String type,  // 아이콘 매핑용 (BANK/CARD/PAY/SECURITIES)
            boolean linked
    ) {}
}
