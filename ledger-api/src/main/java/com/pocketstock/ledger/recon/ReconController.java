package com.pocketstock.ledger.recon;

import com.pocketstock.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 원장 정합성 검산(recon) 온디맨드 조회(#96 item4). 전사 감사라 특정 유저가 아닌 전역 집계.
 * ※ 운영 정보 노출이므로 실서비스에서는 관리자 권한 게이팅 필요(현재는 인증만, TODO).
 */
@RestController
@RequestMapping("/api/recon")
@RequiredArgsConstructor
public class ReconController {

    private final LedgerReconService reconService;

    /** 모든 복식부기 불변식 검산 — balanced=false면 lines에서 깨진 통화/종목이 드러남. */
    @GetMapping("/ledger")
    public ApiResponse<ReconReport> ledger() {
        return ApiResponse.ok("원장 정합성 검산", reconService.verifyAll());
    }
}
