package com.pocketstock.core.asset.mapper;

import com.pocketstock.core.asset.dto.BankAccountResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BankAccountMapper {

    List<BankAccountResponse> findBankAccountsByUser(@Param("userId") Long userId);

    /** 본인 소유 계좌의 인증여부. 소유 아님/없음 → null. */
    Boolean findVerifyStatus(@Param("userId") Long userId, @Param("accountId") Long accountId);

    /** 1원 인증 성공 마킹. 영향 행 수 반환(이미 인증됐으면 0). */
    int markVerified(@Param("userId") Long userId, @Param("accountId") Long accountId);
}
