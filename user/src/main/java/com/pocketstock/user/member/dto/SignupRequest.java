package com.pocketstock.user.member.dto;

/**
 * 회원가입 요청.
 * residentFront(주민번호 앞 6자리, YYMMDD) + residentBack(뒤 1자리)로 생년월일·성별을 도출한다.
 */
public record SignupRequest(
        String username,
        String password,
        String name,
        String residentFront,
        String residentBack,
        String phone
) {}
