package com.pocketstock.user.member.mapper;

import com.pocketstock.user.member.domain.AuthMethod;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMethodMapper {

    /** 사용자+방식으로 인증수단 조회(없으면 null) — PIN 로그인 대조용. */
    AuthMethod findByUserIdAndType(@Param("userId") Long userId, @Param("methodType") String methodType);

    /** PIN/패턴 설정·변경 — (user_id, method_type) UNIQUE 기준 upsert. */
    int upsert(AuthMethod authMethod);
}
