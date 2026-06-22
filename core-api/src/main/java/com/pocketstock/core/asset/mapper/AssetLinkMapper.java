package com.pocketstock.core.asset.mapper;

import com.pocketstock.core.asset.domain.LinkedBankAccountInsert;
import com.pocketstock.core.asset.domain.LinkedInstitution;
import com.pocketstock.core.asset.dto.BankLinkResponse;
import com.pocketstock.core.asset.dto.LinkedRef;
import com.pocketstock.core.asset.dto.MasterRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 자산 연동 실행(쓰기) 전용 Mapper — 연동 시 linked_institutions + linked_* 적재.
 * 조회 전용 LinkedAssetMapper와 책임 분리.
 */
@Mapper
public interface AssetLinkMapper {

    /** companyCode로 카탈로그 마스터 조회(없으면 null). */
    MasterRef findMasterByCode(@Param("companyCode") String companyCode);

    /** 유저↔마스터 커넥션 조회(멱등 판정). 없으면 null. */
    LinkedRef findLinkedInstitution(@Param("userId") Long userId,
                                    @Param("masterId") Long masterId);

    /** 커넥션 노드 INSERT(생성 id는 li.id에 채워짐). */
    void insertLinkedInstitution(LinkedInstitution li);

    /** AVAILABLE 등 비-LINKED 커넥션을 LINKED로 승격(linked_at·last_synced_at 갱신). */
    int markLinked(@Param("id") Long id);

    /** 템플릿 은행 계좌 INSERT(생성 id는 a.id에 채워짐). */
    void insertBankAccount(LinkedBankAccountInsert a);

    int insertCard(@Param("userId") Long userId, @Param("institutionId") Long institutionId,
                   @Param("cardName") String cardName, @Param("cardType") String cardType,
                   @Param("maskedNo") String maskedNo);

    int insertPoint(@Param("userId") Long userId, @Param("institutionId") Long institutionId,
                    @Param("pointName") String pointName, @Param("balance") long balance);

    int insertSecurities(@Param("userId") Long userId, @Param("institutionId") Long institutionId,
                         @Param("depositCash") BigDecimal depositCash, @Param("currency") String currency);

    int insertHolding(@Param("userId") Long userId, @Param("institutionId") Long institutionId,
                      @Param("stockCode") String stockCode, @Param("stockName") String stockName,
                      @Param("quantity") BigDecimal quantity, @Param("evalAmount") BigDecimal evalAmount);

    // ── 멱등 재요청·응답 구성용 조회 ──────────────────────────────────────

    /** 해당 커넥션의 대표(원화) 계좌 — 잔액 큰 순. 없으면 null. */
    BankLinkResponse findRepresentativeAccount(@Param("userId") Long userId,
                                               @Param("institutionId") Long institutionId);

    int countCards(@Param("userId") Long userId, @Param("institutionId") Long institutionId);

    Long findPointBalance(@Param("userId") Long userId, @Param("institutionId") Long institutionId);

    int countHoldings(@Param("userId") Long userId, @Param("institutionId") Long institutionId);

    /** USD(외화) 지갑 잔액 — FX 멱등 판정. 없으면 null. */
    BigDecimal findUsdWalletBalance(@Param("userId") Long userId,
                                    @Param("institutionId") Long institutionId);

    /** refresh — 유저의 모든 연동 기관 last_synced_at을 NOW()로 갱신. @return 갱신 행 수. */
    int touchLastSynced(@Param("userId") Long userId);

    /** 갱신 후 동기화 시각(연동 기관들의 MAX last_synced_at). 연동 기관 없으면 null. */
    java.time.LocalDateTime findMaxLastSyncedAt(@Param("userId") Long userId);
}
