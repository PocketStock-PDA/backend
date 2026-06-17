package com.pocketstock.user.member.dto;

/** 로그아웃 요청 — 폐기할 refreshToken. */
public record LogoutRequest(String refreshToken) {}
