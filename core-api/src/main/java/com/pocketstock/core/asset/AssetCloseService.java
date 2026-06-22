package com.pocketstock.core.asset;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.core.asset.dto.DormantCloseRequest;
import com.pocketstock.core.asset.dto.DormantCloseResponse;
import com.pocketstock.core.asset.dto.DormantCloseRow;
import com.pocketstock.core.asset.mapper.LinkedAssetMapper;
import com.pocketstock.core.client.LedgerFeignClient;
import com.pocketstock.core.client.dto.CmaDormantCreditRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 휴면계좌 일괄 소프트 해지 → CMA 이체(F-D, #140).
 *
 * <p>요청 계좌를 먼저 전부 검증한다(전부-아니면-거부): 본인 소유의 미해지 휴면계좌이거나 과거 이 흐름으로
 * 이미 해지된 계좌가 아니면 — 타인·미존재·비휴면 계좌가 하나라도 있으면 — 원장 호출 전에 400으로 막는다.
 * 검증 통과 후 계좌별로 ① CMA 풀 입금(ledger Feign, 멱등) → ② DB A 소프트 해지(balance=0·closed_at·closed_amount)를
 * 처리한다. 교차 DB(DB A 해지 + DB B 입금)라 한 트랜잭션으로 못 묶으므로 <b>배치 트랜잭션을 열지 않고</b>
 * 계좌별로 독립 커밋한다 — 일부 실패가 앞선 성공을 되돌리지 않게(부분 성공 보존). 입금 후 해지 실패는
 * 같은 요청 재시도로 마무리되며, 멱등키 {@code DORMANT:{accountId}}가 이중 입금을 막는다.
 */
@Service
@RequiredArgsConstructor
public class AssetCloseService {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_ALREADY_CLOSED = "ALREADY_CLOSED";
    private static final String STATUS_FAILED = "FAILED";

    private final LinkedAssetMapper linkedAssetMapper;
    private final LedgerFeignClient ledgerFeignClient;

    public DormantCloseResponse closeDormantAccounts(Long userId, DormantCloseRequest request) {
        List<Long> requested = request.accountIds();
        if (requested == null || requested.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해지할 휴면계좌를 선택해 주세요.");
        }
        if (requested.stream().anyMatch(id -> id == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 계좌가 포함되어 있습니다.");
        }
        // 요청 순서 보존 + 중복 거부(같은 계좌 두 번 체크 = 잘못된 요청)
        Set<Long> ids = new LinkedHashSet<>(requested);
        if (ids.size() != requested.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "중복된 계좌가 포함되어 있습니다.");
        }

        Map<Long, DormantCloseRow> rowById = linkedAssetMapper.findForClose(userId, new ArrayList<>(ids)).stream()
                .collect(Collectors.toMap(DormantCloseRow::accountId, Function.identity()));

        // 1) 전부-아니면-거부 검증: 원장 호출 전에 한 건이라도 부적격이면 400
        for (Long id : ids) {
            DormantCloseRow row = rowById.get(id);
            if (row == null) {
                // 본인 소유 아님 / 미존재
                throw new BusinessException(ErrorCode.INVALID_INPUT, "해지할 수 없는 계좌가 포함되어 있습니다.");
            }
            boolean alreadyClosed = row.closedAt() != null;
            boolean dormant = Boolean.TRUE.equals(row.isDormant());
            if (!alreadyClosed && !dormant) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "휴면계좌가 아닌 계좌가 포함되어 있습니다.");
            }
        }

        // 2) 계좌별 처리(부분 성공 보존 — 배치 트랜잭션 없음)
        List<DormantCloseResponse.Result> results = new ArrayList<>();
        int closedCount = 0;
        BigDecimal transferred = BigDecimal.ZERO;
        boolean allCompleted = true;

        for (Long id : ids) {
            DormantCloseRow row = rowById.get(id);
            if (row.closedAt() != null) {
                // 멱등 재요청: 이미 해지됨 — 원장 재기록 없음, 집계 제외
                results.add(new DormantCloseResponse.Result(
                        id, row.closedAmount(), row.currency(), STATUS_ALREADY_CLOSED));
                continue;
            }
            try {
                BigDecimal amount = row.balance() == null ? BigDecimal.ZERO : row.balance();
                // 잔액 0 휴면계좌는 옮길 돈이 없어 원장 입금을 건너뛰고 해지만 한다(applyEntry는 0 입금을 거부).
                if (amount.signum() > 0) {
                    ledgerFeignClient.creditDormant(
                            new CmaDormantCreditRequest(userId, id, amount, row.currency()));
                }
                linkedAssetMapper.softClose(userId, id, amount);
                results.add(new DormantCloseResponse.Result(id, amount, row.currency(), STATUS_COMPLETED));
                closedCount++;
                transferred = transferred.add(amount);
            } catch (Exception e) {
                // 입금/해지 실행 중 실패 — 같은 요청 재시도로 복구(계좌는 휴면 유지, 멱등키가 이중 입금 차단)
                results.add(new DormantCloseResponse.Result(
                        id, row.balance(), row.currency(), STATUS_FAILED));
                allCompleted = false;
            }
        }

        return new DormantCloseResponse(closedCount, transferred, allCompleted, results);
    }
}
