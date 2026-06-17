package com.pocketstock.user.member.dto;

/** 회원가입 응답 — 생성된 회원 식별자와 아이디. */
public record SignupResponse(Long userId, String username) {}
