package com.pocketstock.ledger.recon;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 원장 정합성 검산(recon, #96 item4) — 우리가 만든 복식부기가 실제로 0으로 닫히는지 읽기 전용 대사.
 *
 * <p>복식부기는 "균형이 가능하게" 만들고, recon은 "균형이 유지되는지 증명/감시"한다. 코드 버그·동시성·
 * 누락으로 한 번 어긋나면 돈·주식이 조용히 새는데, 그걸 잡는 안전망이다. 새 테이블 없이 기존 원장만 합산한다.
 *
 * <p>검사하는 불변식(도메인 횡단):
 * <ul>
 *   <li><b>환전 보존</b>: Σ(고객 CMA fx) + Σ(회사 통화풀 fx) == 0 (통화별) — H5</li>
 *   <li><b>주식 재고 보존</b>: Σ(holdings.quantity) + operating_account.whole_qty == 0 (종목별)</li>
 *   <li><b>회사현금 정합</b>: Σ(operating_cash journal) == operating_cash_balances projection (통화별) — H1</li>
 *   <li><b>매매현금 보존</b>: Σ(유저 예수금 order leg) + Σ(회사현금 order leg) == 0 (통화별)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LedgerReconService {

    private final ReconMapper reconMapper;

    /** 모든 불변식을 검산해 보고서로 묶는다. 하나라도 깨지면 {@code balanced=false}. */
    @Transactional(readOnly = true)
    public ReconReport verifyAll() {
        List<ReconCheck> checks = List.of(
                fxConservation(),
                stockInventoryConservation(),
                operatingCashIntegrity(),
                tradingCashConservation(),
                depositIntegrity(),
                cmaIntegrity()
        );
        boolean balanced = checks.stream().allMatch(ReconCheck::balanced);
        return new ReconReport(balanced, checks);
    }

    /** 환전: 고객 CMA fx leg + 회사 통화풀 fx leg == 0 (통화별, H5). */
    private ReconCheck fxConservation() {
        Map<String, BigDecimal> customer = toMap(reconMapper.sumCmaByRefType("FX_TX"));
        Map<String, BigDecimal> firm = toMap(reconMapper.sumOperatingCashByRefType("fx"));
        return conservation("fx_conservation",
                "Σ(고객 CMA fx) + Σ(회사 통화풀 fx) == 0 (통화별)", customer, firm);
    }

    /** 주식 재고: 유저 보유 수량 + 회사 옴니버스 재고(정수+소수) == 0 (종목별). */
    private ReconCheck stockInventoryConservation() {
        Map<String, BigDecimal> holdings = toMap(reconMapper.sumHoldingsByStock());
        Map<String, BigDecimal> firm = toMap(reconMapper.operatingAccountInventory());
        return conservation("stock_inventory_conservation",
                "Σ(holdings.quantity) + operating_account(whole_qty + fractional_remainder) == 0 (종목별)",
                holdings, firm);
    }

    /** 회사현금: 불변 journal 합 == projection 잔액 (통화별, H1). */
    private ReconCheck operatingCashIntegrity() {
        Map<String, BigDecimal> journal = toMap(reconMapper.sumOperatingCashByRefType(null));
        Map<String, BigDecimal> projection = toMap(reconMapper.operatingCashBalances());
        return integrity("operating_cash_integrity",
                "Σ(operating_cash journal) == operating_cash_balances projection (통화별)", journal, projection);
    }

    /** 매매현금: 유저 예수금 order leg + 회사현금 order leg == 0 (통화별). */
    private ReconCheck tradingCashConservation() {
        Map<String, BigDecimal> user = toMap(reconMapper.sumDepositByRefType("order"));
        Map<String, BigDecimal> firm = toMap(reconMapper.sumOperatingCashByRefType("order"));
        return conservation("trading_cash_conservation",
                "Σ(유저 예수금 order leg) + Σ(회사현금 order leg) == 0 (통화별)", user, firm);
    }

    /** 유저 예수금: 불변 journal 합 == projection 잔액 (계좌별). */
    private ReconCheck depositIntegrity() {
        Map<String, BigDecimal> journal = toMap(reconMapper.sumDepositByAccount());
        Map<String, BigDecimal> projection = toMap(reconMapper.accountBalances());
        return integrity("deposit_integrity",
                "Σ(deposit_transactions) == account_balances.balance (계좌별)", journal, projection);
    }

    /** 고객 CMA: 불변 journal 합 == projection 잔액 (계좌·통화별). */
    private ReconCheck cmaIntegrity() {
        Map<String, BigDecimal> journal = toMap(reconMapper.sumCmaAll());
        Map<String, BigDecimal> projection = toMap(reconMapper.cmaBalances());
        return integrity("cma_integrity",
                "Σ(cma_transactions) == cma_balances.balance (계좌·통화별)", journal, projection);
    }

    /** 보존 불변식: 두 변의 합이 0이어야 정상(diff = left + right). */
    private ReconCheck conservation(String name, String invariant,
                                    Map<String, BigDecimal> left, Map<String, BigDecimal> right) {
        return build(name, invariant, left, right, true);
    }

    /** 정합 불변식: 두 변이 같아야 정상(diff = left − right). */
    private ReconCheck integrity(String name, String invariant,
                                 Map<String, BigDecimal> left, Map<String, BigDecimal> right) {
        return build(name, invariant, left, right, false);
    }

    private ReconCheck build(String name, String invariant,
                             Map<String, BigDecimal> left, Map<String, BigDecimal> right, boolean add) {
        List<ReconLine> lines = new ArrayList<>();
        boolean allBalanced = true;
        for (String key : new TreeSet<>(union(left, right))) {
            BigDecimal l = left.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal r = right.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal diff = add ? l.add(r) : l.subtract(r);
            boolean balanced = diff.signum() == 0;
            allBalanced &= balanced;
            lines.add(new ReconLine(key, l, r, diff, balanced));
        }
        return new ReconCheck(name, invariant, allBalanced, lines);
    }

    /** recon_key/recon_val 정규화 행을 Map으로. recon_val은 BigDecimal/정수 무엇이든 안전 변환(없으면 0). */
    private Map<String, BigDecimal> toMap(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("reconVal");
            m.put((String) row.get("reconKey"),
                    value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()));
        }
        return m;
    }

    private java.util.Set<String> union(Map<String, BigDecimal> a, Map<String, BigDecimal> b) {
        java.util.Set<String> keys = new TreeSet<>(a.keySet());
        keys.addAll(b.keySet());
        return keys;
    }
}
