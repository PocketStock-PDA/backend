package com.pocketstock.user.member.dto;

/** ID/PW 로그인 요청. */
public record LoginRequest(String username, String password) {}
