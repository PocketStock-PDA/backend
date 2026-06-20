package com.pocketstock.ledger.dev;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.dto.OpenAccountRequest;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.service.DepositService;
import com.pocketstock.ledger.trading.service.SecuritiesAccountService;
import com.pocketstock.user.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 로컬 전용 개발 테스트 하니스. local 프로파일에서만 등록된다.
 * - GET /dev          : 시세/WS 수동 테스트 페이지(resources/dev/dev.html)
 * - GET /dev/token    : 테스트용 JWT 발급(로그인 도메인 없이도 보호 API 호출)
 */
@Slf4j
@Profile("local")
@RestController
@RequiredArgsConstructor
public class DevController {

    private final JwtProvider jwtProvider;
    private final SecuritiesAccountService accountService;
    private final SecuritiesAccountMapper accountMapper;
    private final DepositService depositService;

    @GetMapping(value = "/dev", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String page() throws IOException {
        // getContentAsString이 내부 스트림을 열고 닫는다(누수 없음)
        return new ClassPathResource("dev/dev.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping("/dev/token")
    public ApiResponse<Map<String, String>> token(@RequestParam(defaultValue = "1") Long userId) {
        log.info("[DEV] 테스트 토큰 발급 userId={}", userId);
        return ApiResponse.ok("테스트 토큰 발급", Map.of(
                "userId", String.valueOf(userId),
                "token", jwtProvider.createToken(userId)));
    }

    /** 국내 위탁계좌 보장(없으면 개설) + KRW 예수금 충전 — 온주 매수 테스트용. */
    @GetMapping("/dev/deposit")
    public ApiResponse<Map<String, String>> deposit(@RequestParam(defaultValue = "1") Long userId,
                                                    @RequestParam(defaultValue = "10000000") BigDecimal amount) {
        accountService.open(userId, new OpenAccountRequest(List.of("DOMESTIC")));
        SecuritiesAccount account = accountMapper.findByUserIdAndMarket(userId, "DOMESTIC");
        BigDecimal balance = depositService.record(userId, account.getId(), "IN_TRANSFER",
                amount, "KRW", "dev", null, null);   // dev 충전 — 멱등키 불요
        log.info("[DEV] 예수금 충전 userId={} amount={} balance={}", userId, amount, balance);
        return ApiResponse.ok("예수금 충전 완료", Map.of("balance", balance.toPlainString()));
    }
}
