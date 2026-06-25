package com.pocketstock.ledger.cma.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.cma.dto.request.AutoChargeSettingRequest;
import com.pocketstock.ledger.cma.dto.request.CmaDepositRequest;
import com.pocketstock.ledger.cma.dto.request.CmaTransferRequest;
import com.pocketstock.ledger.cma.dto.request.CollectionSettingRequest;
import com.pocketstock.ledger.cma.dto.response.AutoChargeSettingResponse;
import com.pocketstock.ledger.cma.dto.response.CmaAccountResponse;
import com.pocketstock.ledger.cma.dto.response.CmaBalanceResponse;
import com.pocketstock.ledger.cma.dto.response.CmaDepositResponse;
import com.pocketstock.ledger.cma.dto.response.CmaHomeResponse;
import com.pocketstock.ledger.cma.dto.response.CmaTransactionResponse;
import com.pocketstock.ledger.cma.dto.response.CmaTransferResponse;
import com.pocketstock.ledger.cma.dto.response.CollectResult;
import com.pocketstock.ledger.cma.dto.response.CollectionSettingView;
import com.pocketstock.ledger.cma.service.CmaAccountService;
import com.pocketstock.ledger.cma.service.CmaAutoChargeSettingService;
import com.pocketstock.ledger.cma.service.CmaCollectService;
import com.pocketstock.ledger.cma.service.CmaDepositService;
import com.pocketstock.ledger.cma.service.CmaQueryService;
import com.pocketstock.ledger.cma.service.CmaTransferService;
import com.pocketstock.user.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cma")
@RequiredArgsConstructor
public class CmaController {

    private final CmaQueryService queryService;
    private final CmaCollectService collectService;
    private final CmaAccountService accountService;
    private final CmaAutoChargeSettingService autoChargeSettingService;
    private final CmaTransferService transferService;
    private final CmaDepositService depositService;

    /** CMA 계좌 개설(멱등) — 온보딩 마지막 단계. 이미 있으면 기존 계좌 반환. */
    @PostMapping("/account")
    public ApiResponse<CmaAccountResponse> openAccount(@CurrentUserId Long userId) {
        return ApiResponse.ok("CMA 계좌 개설 성공", accountService.openOrGet(userId));
    }

    @GetMapping("/home")
    public ApiResponse<CmaHomeResponse> getHome(@CurrentUserId Long userId) {
        return ApiResponse.ok("홈 대시보드 조회 성공", queryService.getHome(userId));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<CmaTransactionResponse>> getTransactions(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String txType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ApiResponse.ok("계좌내역 조회 성공",
                queryService.getTransactions(userId, txType, from, to, safePage, safeSize));
    }

    @GetMapping("/balance")
    public ApiResponse<CmaBalanceResponse> getBalance(@CurrentUserId Long userId) {
        return ApiResponse.ok("CMA 잔액 조회 성공", queryService.getBalance(userId));
    }

    /** 통합 수집 — 활성 소스 전체 실행(부분 성공 허용). 멱등키는 소스별 접미사로 파생된다. */
    @PostMapping("/collect")
    public ApiResponse<List<CollectResult>> collectAll(
            @CurrentUserId Long userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok("잔돈 모으기 실행 완료",
                collectService.collectAll(userId, resolveKey(idempotencyKey)));
    }

    @PostMapping("/collect/account")
    public ApiResponse<CollectResult> collectAccount(
            @CurrentUserId Long userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok("계좌 끝전 적립 완료",
                collectService.collectFromAccount(userId, resolveKey(idempotencyKey)));
    }

    @PostMapping("/collect/card")
    public ApiResponse<CollectResult> collectCard(
            @CurrentUserId Long userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok("카드 라운드업 적립 완료",
                collectService.collectFromCard(userId, resolveKey(idempotencyKey)));
    }

    @PostMapping("/collect/point")
    public ApiResponse<CollectResult> collectPoint(
            @CurrentUserId Long userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok("포인트 전환 적립 완료",
                collectService.collectFromPoint(userId, resolveKey(idempotencyKey)));
    }

    /** 외화 잔액 적립 — 연동 USD 지갑 전액을 CMA 달러 풀로 입금(환전 없음, USD→USD). */
    @PostMapping("/collect/fx")
    public ApiResponse<CollectResult> collectFx(
            @CurrentUserId Long userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok("외화 잔액 적립 완료",
                collectService.collectFromFx(userId, resolveKey(idempotencyKey)));
    }

    @GetMapping("/collect/history")
    public ApiResponse<List<CmaTransactionResponse>> getCollectHistory(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ApiResponse.ok("적립 이력 조회 성공",
                queryService.getCollectHistory(userId, safePage, safeSize));
    }

    @GetMapping("/transfers")
    public ApiResponse<List<CmaTransactionResponse>> getTransfers(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ApiResponse.ok("자금 이동 이력 조회 성공",
                queryService.getTransfers(userId, safePage, safeSize));
    }

    /**
     * CMA 풀 → 위탁 예수금 자금이동(BUY_TRANSFER) — 모은 자금을 매수용 예수금으로 충전.
     * market(DOMESTIC/OVERSEAS)이 출금 통화풀·입금 예수금을 함께 결정한다. 사전 txn-auth 필요.
     */
    @PostMapping("/transfer")
    public ApiResponse<CmaTransferResponse> transfer(
            @CurrentUserId Long userId,
            @RequestBody @Valid CmaTransferRequest request) {
        return ApiResponse.ok("CMA 자금 이동 성공", transferService.transfer(userId, request));
    }

    /**
     * 은행계좌 → CMA 원화풀 부족분 충전(DEPOSIT) — 매수 목표 금액과 CMA 잔액의 차액만 은행에서 끌어와 충전.
     * 이미 충분하면 이체하지 않는다(KRW 전용). 사전 txn-auth 필요.
     */
    @PostMapping("/deposit")
    public ApiResponse<CmaDepositResponse> deposit(
            @CurrentUserId Long userId,
            @RequestBody @Valid CmaDepositRequest request) {
        return ApiResponse.ok("CMA 충전 성공", depositService.deposit(userId, request));
    }

    @GetMapping("/collect/settings")
    public ApiResponse<List<CollectionSettingView>> getSettings(@CurrentUserId Long userId) {
        return ApiResponse.ok("수집 소스 설정 조회 성공", collectService.getSettings(userId));
    }

    @PutMapping("/collect/settings")
    public ApiResponse<Void> updateSettings(
            @CurrentUserId Long userId,
            @RequestBody @Valid CollectionSettingRequest request) {
        collectService.updateSettings(userId, request);
        return ApiResponse.ok("적립 소스 설정 완료", null);
    }

    /** 부족금액 자동충전 설정 조회 — 설정 없는 신규 사용자는 OFF 기본값 반환. */
    @GetMapping("/auto-charge-settings")
    public ApiResponse<AutoChargeSettingResponse> getAutoChargeSettings(@CurrentUserId Long userId) {
        return ApiResponse.ok("자동충전 설정 조회 성공", autoChargeSettingService.get(userId));
    }

    /** 부족금액 자동충전 설정 변경 — 켤 때만 충전 재원·한도 검증. */
    @PutMapping("/auto-charge-settings")
    public ApiResponse<Void> updateAutoChargeSettings(
            @CurrentUserId Long userId,
            @RequestBody @Valid AutoChargeSettingRequest request) {
        autoChargeSettingService.update(userId, request);
        return ApiResponse.ok("자동충전 설정 완료", null);
    }

    /**
     * 헤더가 유효하면 클라이언트 멱등키 사용, 비어 있거나(공백 포함) 없으면 서버에서 1회용 키 생성.
     * 공백 키("", "  ")를 그대로 두면 서로 다른 요청이 같은 키로 충돌해 잘못 멱등 처리될 수 있어 정규화한다.
     */
    private static String resolveKey(String idempotencyKey) {
        return (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : UUID.randomUUID().toString();
    }
}
