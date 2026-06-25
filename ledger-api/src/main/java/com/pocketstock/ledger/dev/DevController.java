package com.pocketstock.ledger.dev;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.calendar.DividendBatchService;
import com.pocketstock.ledger.calendar.EarningsBatchService;
import com.pocketstock.ledger.cma.domain.CmaAccount;
import com.pocketstock.ledger.cma.mapper.CmaAccountMapper;
import com.pocketstock.ledger.cma.service.CmaAccountService;
import com.pocketstock.ledger.cma.service.CmaLedgerWriter;
import com.pocketstock.ledger.exchange.CurrencyRateCache;
import com.pocketstock.ledger.exchange.client.YahooFxClient;
import com.pocketstock.ledger.outbox.Outbox;
import com.pocketstock.ledger.outbox.OutboxMapper;
import com.pocketstock.ledger.exchange.dto.response.CurrencyRateResponse;
import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import com.pocketstock.ledger.trading.dto.OpenAccountRequest;
import com.pocketstock.ledger.trading.mapper.SecuritiesAccountMapper;
import com.pocketstock.ledger.trading.service.AutoInvestScheduler;
import com.pocketstock.ledger.trading.matching.AutoInvestTriggerEngine;
import com.pocketstock.ledger.trading.service.DailyValuationService;
import com.pocketstock.ledger.trading.service.DepositService;
import com.pocketstock.ledger.trading.service.SecuritiesAccountService;
import com.pocketstock.user.security.TxnAuth;
import com.pocketstock.user.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 로컬 전용 개발 테스트 하니스. local 프로파일에서만 등록된다.
 * - GET /dev          : 시세/WS 수동 테스트 페이지(resources/dev/dev.html)
 * - GET /dev/token    : 테스트용 JWT 발급(로그인 도메인 없이도 보호 API 호출)
 * - GET /dev/txn-auth : 거래 인증 키 발급(회원 도메인 /verify가 core-api에만 있어 8082에서 대체)
 * - GET /dev/cma-credit : 은행→CMA 풀 직접 적립(매수 자금원 마련)
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
    private final DividendBatchService dividendBatchService;
    private final EarningsBatchService earningsBatchService;
    private final AutoInvestScheduler autoInvestScheduler;
    private final AutoInvestTriggerEngine autoInvestTriggerEngine;
    private final DailyValuationService dailyValuationService;
    private final OutboxMapper outboxMapper;
    private final StringRedisTemplate redis;
    private final CmaAccountService cmaAccountService;
    private final CmaAccountMapper cmaAccountMapper;
    private final CmaLedgerWriter cmaLedgerWriter;
    private final CurrencyRateCache currencyRateCache;
    private final YahooFxClient yahooFxClient;

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

    /**
     * 거래 인증 키 발급 — 회원 도메인 {@code /api/users/account-password/verify}가 core-api에만 있어
     * ledger-api(8082)에서 직접 못 부르므로 dev 대체. Redis {@code txn-auth:{userId}}에 유지 세션(KEEP)을
     * 30분 발급해 매매/환전/CMA가 {@link com.pocketstock.user.security.TxnAuthGuard}를 통과한다.
     */
    @GetMapping("/dev/txn-auth")
    public ApiResponse<Map<String, String>> txnAuth(@RequestParam(defaultValue = "1") Long userId) {
        redis.opsForValue().set(TxnAuth.key(userId), TxnAuth.VALUE_KEEP, TxnAuth.TTL);
        log.info("[DEV] 거래 인증 키 발급 userId={} ttl={}분", userId, TxnAuth.TTL.toMinutes());
        return ApiResponse.ok("거래 인증 완료(30분 유지)", Map.of(
                "userId", String.valueOf(userId),
                "ttlMinutes", String.valueOf(TxnAuth.TTL.toMinutes())));
    }

    /**
     * 은행 → CMA 원화풀 직접 적립 — 매수 자금원 마련용. CMA 계좌 보장 후 KRW 풀에 DEPOSIT 1줄 기록한다.
     * 달러풀은 직접 충전하지 않는다(환전·자동환전으로만 채워 실제 모델과 동일하게).
     */
    @GetMapping("/dev/cma-credit")
    public ApiResponse<Map<String, String>> cmaCredit(@RequestParam(defaultValue = "1") Long userId,
                                                      @RequestParam(defaultValue = "10000000") BigDecimal amount) {
        cmaAccountService.openOrGet(userId);   // 시드엔 있지만 없는 환경에서도 무해하게 보장
        CmaAccount account = cmaAccountMapper.findByUserId(userId);
        BigDecimal balance = cmaLedgerWriter.applyEntry(userId, account.getId(), "KRW",
                "DEPOSIT", "BANK", amount, null, null, null);   // dev 충전 — 멱등키 불요
        log.info("[DEV] 은행→CMA 원화풀 적립 userId={} amount={} balance={}", userId, amount, balance);
        return ApiResponse.ok("CMA 원화풀 충전 완료", Map.of("krwPool", balance.toPlainString()));
    }

    /**
     * 환율 파이프라인 헬스체크 — 야후 폴백 소스 생존 + Redis 캐시(WS가 채우는 SSOT) 상태를 한 번에 진단.
     * cache.present=false면 WS 첫 틱 미수신(콜드스타트), yahoo.alive=false면 폴백 소스 단절.
     * 둘 다 false라야 매수·환전이 502로 막힌다(하나라도 살아 있으면 provider가 환율을 공급).
     */
    @GetMapping("/dev/fx-health")
    public ApiResponse<Map<String, Object>> fxHealth() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1) 야후 폴백 소스 — 직접 1회 호출(캐시 거치지 않음)
        CurrencyRateResponse yahoo = yahooFxClient.fetchUsdKrw();
        result.put("yahoo", yahoo == null
                ? Map.of("alive", false)
                : Map.of("alive", true, "rate", yahoo.exchangeRate(),
                        "change", yahoo.change(), "fetchedAt", yahoo.updatedAt()));

        // 2) Redis 캐시(WS가 채우는 SSOT) — 폴백 없이 raw 조회(WS·Redis 생존 프록시)
        CurrencyRateResponse cached = currencyRateCache.get();
        result.put("cache", cached == null
                ? Map.of("present", false, "note", "WS 첫 틱 미수신 또는 Redis 비어있음(콜드스타트)")
                : Map.of("present", true, "rate", cached.exchangeRate(), "updatedAt", cached.updatedAt()));

        log.info("[DEV] FX health — yahoo.alive={} cache.present={}", yahoo != null, cached != null);
        return ApiResponse.ok("환율 헬스체크", result);
    }

    /** KIS 배당일정 배치 수동 트리거 — 배치 동작 확인용. */
    @GetMapping("/dev/dividend-batch")
    public ApiResponse<String> triggerDividendBatch() {
        log.info("[DEV] 배당배치 수동 트리거");
        dividendBatchService.syncDividendEvents();
        return ApiResponse.ok("배당 배치 완료", null);
    }

    /** OpenDART 실적발표 배치 수동 트리거 — 배치 동작 확인용. */
    @GetMapping("/dev/earnings-batch")
    public ApiResponse<String> triggerEarningsBatch() {
        log.info("[DEV] 실적배치 수동 트리거");
        earningsBatchService.syncEarningsEvents();
        return ApiResponse.ok("실적 배치 완료", null);
    }

    /** 자동모으기 정기매수 스케줄러 수동 트리거(market=DOMESTIC/OVERSEAS) — cron(9:10/22:40) 안 기다리고 즉시 집행. */
    @GetMapping("/dev/auto-invest-run")
    public ApiResponse<String> triggerAutoInvest(@RequestParam(defaultValue = "DOMESTIC") String market) {
        log.info("[DEV] 자동모으기 수동 집행 트리거 — {}", market);
        autoInvestScheduler.run(market.toUpperCase());
        return ApiResponse.ok(market + " 자동모으기 집행 완료", null);
    }

    /**
     * 수익률 트리거 강제 평가(#194 실시간) — 실시간 틱은 장중에만 오므로, 그 종목 현재가(캐시/REST)로
     * 즉시 1회 평가한다. 운영은 호가 틱마다 자동(AutoInvestTriggerEngine). 선행: 그 종목에 트리거 등록돼 있어야 함.
     */
    @GetMapping("/dev/auto-invest-trigger-run")
    public ApiResponse<String> triggerAutoInvestTrigger(@RequestParam(defaultValue = "005930") String stockCode) {
        log.info("[DEV] 수익률 트리거 강제 평가 — {}", stockCode);
        int n = autoInvestTriggerEngine.devEvaluate(stockCode);
        return ApiResponse.ok(stockCode + " 트리거 " + n + "건 평가 완료", null);
    }

    /** 일별 평가 스냅샷 배치(BATCH-002) 수동 트리거 — cron(07시) 안 기다리고 즉시 적재. */
    @GetMapping("/dev/daily-valuation-run")
    public ApiResponse<String> triggerDailyValuation() {
        log.info("[DEV] 일별 평가 스냅샷(BATCH-002) 수동 트리거");
        dailyValuationService.run();
        return ApiResponse.ok("일별 평가 스냅샷 적재 완료", null);
    }

    /** 최근 outbox 이벤트 조회(#204 검증) — published=true면 Kafka 발행 성공. */
    @GetMapping("/dev/outbox")
    public ApiResponse<List<Outbox>> outbox(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok("outbox 최근 이벤트", outboxMapper.findRecent(limit));
    }
}
