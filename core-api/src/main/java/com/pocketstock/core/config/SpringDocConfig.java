package com.pocketstock.core.config;

import com.pocketstock.user.security.CurrentUserId;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class SpringDocConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OperationCustomizer hideCurrentUserIdParam() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            if (operation.getParameters() == null) return operation;
            operation.getParameters().removeIf(param ->
                    java.util.Arrays.stream(handlerMethod.getMethodParameters())
                            .anyMatch(mp ->
                                    mp.hasParameterAnnotation(CurrentUserId.class)
                                    && mp.getParameterName() != null
                                    && mp.getParameterName().equals(param.getName())
                            )
            );
            return operation;
        };
    }
}
