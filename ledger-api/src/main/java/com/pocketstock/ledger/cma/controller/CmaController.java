package com.pocketstock.ledger.cma.controller;

import com.pocketstock.common.response.ApiResponse;
import com.pocketstock.ledger.cma.dto.response.CmaHomeResponse;
import com.pocketstock.ledger.cma.service.CmaQueryService;
import com.pocketstock.user.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cma")
@RequiredArgsConstructor
public class CmaController {

    private final CmaQueryService queryService;

    @GetMapping("/home")
    public ApiResponse<CmaHomeResponse> getHome(@CurrentUserId Long userId) {
        return ApiResponse.ok("홈 대시보드 조회 성공", queryService.getHome(userId));
    }
}
