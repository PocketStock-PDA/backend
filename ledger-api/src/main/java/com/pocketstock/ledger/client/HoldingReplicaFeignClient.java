package com.pocketstock.ledger.client;

import com.pocketstock.ledger.client.dto.HoldingReplicaUpsertRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** ledger → core 보유 복제 동기화. core-api {@code holdings_replica} upsert(비파괴). */
@FeignClient(name = "core-api-holding-replica", url = "${feign.core-api.url}")
public interface HoldingReplicaFeignClient {

    @PostMapping("/internal/holdings/replica")
    void upsertReplica(@RequestBody List<HoldingReplicaUpsertRequest> rows);
}
