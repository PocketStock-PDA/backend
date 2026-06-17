package com.pocketstock.user.member.mapper;

import com.pocketstock.user.member.domain.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    int countByUsername(@Param("username") String username);

    /** 회원 INSERT. 성공 후 member.id에 생성된 PK가 채워진다(useGeneratedKeys). */
    int insertMember(Member member);

    /** 로그인용 — username으로 회원 조회(없으면 null). */
    Member findByUsername(@Param("username") String username);
}
