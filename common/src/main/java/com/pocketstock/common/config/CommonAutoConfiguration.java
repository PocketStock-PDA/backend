package com.pocketstock.common.config;

import com.pocketstock.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * common 모듈을 의존하는 서비스에 공통 빈을 자동 등록.
 * GlobalExceptionHandler는 MVC(SERVLET) 환경에서만 — api-gateway(WebFlux)는 제외.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(GlobalExceptionHandler.class)
public class CommonAutoConfiguration {
}
