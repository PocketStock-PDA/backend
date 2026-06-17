package com.pocketstock.core.internal.asset;

import com.pocketstock.core.internal.asset.dto.CardRoundupSummary;
import com.pocketstock.core.internal.asset.dto.LinkedAccountSummary;
import com.pocketstock.core.internal.asset.dto.PointSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/assets")
@RequiredArgsConstructor
public class InternalAssetController {

    private final InternalAssetService service;

    @GetMapping("/accounts")
    public List<LinkedAccountSummary> getLinkedAccounts(
            @RequestParam Long userId,
            @RequestParam List<Long> enabledIds) {
        return service.getLinkedAccounts(userId, enabledIds);
    }

    @GetMapping("/card-roundup")
    public CardRoundupSummary getCardRoundup(
            @RequestParam Long userId,
            @RequestParam Long linkedAccountId) {
        return service.getCardRoundup(userId, linkedAccountId);
    }

    @PatchMapping("/card-roundup/mark-collected")
    public void markRoundupCollected(
            @RequestParam Long userId,
            @RequestBody List<Long> cardTransactionIds) {
        service.markRoundupCollected(userId, cardTransactionIds);
    }

    @GetMapping("/points")
    public PointSummary getAvailablePoints(
            @RequestParam Long userId,
            @RequestParam Long linkedAccountId) {
        return service.getAvailablePoints(userId, linkedAccountId);
    }
}
