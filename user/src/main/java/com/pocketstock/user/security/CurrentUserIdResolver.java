package com.pocketstock.user.security;

import com.pocketstock.user.security.jwt.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @CurrentUserId 파라미터에 JwtAuthFilter가 심은 user_id를 주입한다.
 * spring-security 없이 request attribute에서 직접 읽음.
 */
@Component
public class CurrentUserIdResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest webRequest, WebDataBinderFactory binder) {
        HttpServletRequest req = webRequest.getNativeRequest(HttpServletRequest.class);
        return req != null ? req.getAttribute(JwtAuthFilter.USER_ID_ATTR) : null;
    }
}
