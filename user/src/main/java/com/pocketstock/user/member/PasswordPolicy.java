package com.pocketstock.user.member;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 비밀번호 보안 규칙 공용 검증기.
 * 규칙: 길이 8자 이상 / 대문자 / 소문자 / 숫자 / 특수문자 각 1개 이상.
 * 회원가입·비밀번호 재설정·실시간 검증에서 공유한다.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPER   = Pattern.compile("[A-Z]");
    private static final Pattern LOWER   = Pattern.compile("[a-z]");
    private static final Pattern DIGIT   = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private PasswordPolicy() {
    }

    /**
     * 실패한 규칙 코드 목록을 반환한다. 비어 있으면 모든 규칙 통과.
     * null도 NPE 없이 전 규칙을 평가한다(모두 실패로 집계).
     */
    public static List<String> validate(String password) {
        String pw = password == null ? "" : password;
        List<String> failed = new ArrayList<>();
        if (pw.length() < MIN_LENGTH)      failed.add("MIN_LENGTH");
        if (!UPPER.matcher(pw).find())     failed.add("UPPERCASE");
        if (!LOWER.matcher(pw).find())     failed.add("LOWERCASE");
        if (!DIGIT.matcher(pw).find())     failed.add("DIGIT");
        if (!SPECIAL.matcher(pw).find())   failed.add("SPECIAL");
        return failed;
    }

    /** 모든 규칙을 통과하면 true. */
    public static boolean isValid(String password) {
        return validate(password).isEmpty();
    }
}
