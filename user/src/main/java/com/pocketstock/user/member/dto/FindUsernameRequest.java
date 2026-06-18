package com.pocketstock.user.member.dto;

/** 아이디 찾기 요청. 이름+휴대폰으로 본인확인 후 마스킹된 아이디를 반환한다. */
public record FindUsernameRequest(String name, String phone) {
}
