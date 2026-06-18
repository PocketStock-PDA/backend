package com.pocketstock.user.member.dto;

/** 아이디 찾기 응답. 아이디는 일부만 노출(마스킹). */
public record FindUsernameResponse(String maskedUsername) {
}
