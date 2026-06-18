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

    /** id로 회원 조회(없으면 null). 비밀번호 검증 등 인증된 작업에 사용. */
    Member findById(@Param("id") Long id);

    /** 비밀번호 해시 변경. 변경된 행 수 반환. */
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /** 아이디 찾기 — 이름+휴대폰으로 회원 조회(없으면 null). */
    Member findByNameAndPhone(@Param("name") String name, @Param("phone") String phone);

    /** 비밀번호 재설정 — 아이디+휴대폰으로 본인확인 조회(없으면 null). */
    Member findByUsernameAndPhone(@Param("username") String username, @Param("phone") String phone);

    /** PIN 로그인 — device_id로 회원 조회(없으면 null). */
    Member findByDeviceId(@Param("deviceId") String deviceId);

    /** 기기 등록 전, 동일 device_id를 가진 기존 소유자에서 분리(기기 1대=계정 1명 보장). */
    int clearDeviceId(@Param("deviceId") String deviceId);

    /** 기기 등록 — 로그인 사용자에 device_id 부여. */
    int updateDeviceId(@Param("id") Long id, @Param("deviceId") String deviceId);
}
