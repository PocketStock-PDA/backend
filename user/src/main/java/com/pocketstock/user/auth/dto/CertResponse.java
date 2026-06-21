package com.pocketstock.user.auth.dto;

/**
 * 난수문자 인증요청 응답.
 * randomCode는 프론트가 "문자 보내기" 화면에 표시하고, 보내기 시 verify로 echo 제출한다.
 */
public record CertResponse(String requestId, String randomCode, int expiresIn) {
}
