package com.pocketstock.user.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 현재 인증된 user_id를 주입.
 * 예) public Xxx get(@CurrentUserId Long userId) { ... }
 * ※ ArgumentResolver는 추후 구현(SecurityContext에서 추출).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
