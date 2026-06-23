package com.pocketstock.ledger.client;

import com.pocketstock.ledger.client.dto.CardRoundupSummary;
import com.pocketstock.ledger.client.dto.LinkedAccountSummary;
import com.pocketstock.ledger.client.dto.PointSummary;
import com.pocketstock.ledger.client.dto.SourceDeduction;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "core-api", url = "${feign.core-api.url}")
public interface AssetFeignClient {

    @GetMapping("/internal/assets/accounts")
    List<LinkedAccountSummary> getLinkedAccounts(@RequestParam("userId") Long userId,
                                                  @RequestParam("enabledIds") List<Long> enabledIds);

    @GetMapping("/internal/assets/fx-wallets")
    List<LinkedAccountSummary> getUsdWallets(@RequestParam("userId") Long userId);

    @GetMapping("/internal/assets/card-roundup")
    CardRoundupSummary getCardRoundup(@RequestParam("userId") Long userId,
                                       @RequestParam("linkedAccountId") Long linkedAccountId);

    @PatchMapping("/internal/assets/card-roundup/mark-collected")
    void markRoundupCollected(@RequestParam("userId") Long userId,
                               @RequestBody List<Long> cardTransactionIds);

    @GetMapping("/internal/assets/points")
    PointSummary getAvailablePoints(@RequestParam("userId") Long userId,
                                     @RequestParam("linkedAccountId") Long linkedAccountId);

    @PatchMapping("/internal/assets/accounts/deduct")
    void deductAccountBalances(@RequestParam("userId") Long userId,
                               @RequestBody List<SourceDeduction> deductions);

    @PatchMapping("/internal/assets/points/deduct")
    void deductPointBalances(@RequestParam("userId") Long userId,
                             @RequestBody List<SourceDeduction> deductions);
}
