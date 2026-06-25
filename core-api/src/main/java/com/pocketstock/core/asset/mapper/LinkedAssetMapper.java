package com.pocketstock.core.asset.mapper;

import com.pocketstock.core.asset.dto.DormantAccountResponse;
import com.pocketstock.core.asset.dto.DormantCloseRow;
import com.pocketstock.core.asset.dto.LinkedCardResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 사용자 연동 자산(linked_*) 조회 전용 Mapper.
 * 내부 Feign용 InternalAssetMapper와 책임 분리(이쪽은 사용자 화면 조회).
 */
@Mapper
public interface LinkedAssetMapper {

    /** 휴면(is_dormant=true, 미해지) 은행 계좌 목록. 잔액 큰 순. */
    List<DormantAccountResponse> findDormantAccounts(@Param("userId") Long userId);

    /**
     * 외화(USD) 지갑 잔액 합계 — 잔돈 스캔의 FX(환전 잔돈) 소스.
     * 휴면 계좌는 제외(휴면 해지 흐름이 별도 처리). 행 없으면 0 반환.
     */
    BigDecimal sumUsdWalletBalance(@Param("userId") Long userId);

    /**
     * 휴면계좌 해지 검증·분류용 본인 소유 계좌 조회(요청 id들). 미존재/타인 계좌는 결과에서 빠진다(서비스가 400 판정).
     * 멱등 재요청 대비 이미 해지된 계좌도 함께 반환한다(closed_at으로 ALREADY_CLOSED 판정).
     */
    List<DormantCloseRow> findForClose(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    /** 사용자 연동 카드 목록 — 잔돈 모으기 카드 선택 화면용. */
    List<LinkedCardResponse> findLinkedCards(@Param("userId") Long userId);

    /**
     * 휴면계좌 소프트 해지 — balance=0, closed_at=NOW(), closed_amount 기록.
     * 가드(is_dormant=TRUE AND closed_at IS NULL)로 이미 해지된 계좌·비휴면 계좌는 영향 0(멱등).
     * @return 갱신된 행 수(1=해지 성공, 0=이미 해지/대상 아님)
     */
    int softClose(@Param("userId") Long userId,
                  @Param("accountId") Long accountId,
                  @Param("closedAmount") BigDecimal closedAmount);
}
