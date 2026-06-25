package com.pocketstock.user.member.mapper;

import com.pocketstock.user.member.domain.AccountPassword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountPasswordMapper {

    /** userId로 계좌 비밀번호 조회(없으면 null). */
    AccountPassword findByUserId(@Param("userId") Long userId);

    /** 계좌 비밀번호 설정/변경 — user_id UNIQUE 기준 upsert. */
    int upsert(AccountPassword accountPassword);

    /** 잠금/해제 전용 — is_locked만 갱신(자동잠금 5회 도달, 휴대폰 인증 해제). */
    int updateLockStatus(@Param("userId") Long userId, @Param("isLocked") boolean isLocked);
}
