package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.Order;
import com.pocketstock.ledger.trading.domain.OrderStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Mapper
public interface OrderMapper {

    /** 주문 INSERT (useGeneratedKeys → id 채움) */
    int insert(Order order);

    /** 멱등키(client_order_id)로 기존 주문 조회 — 재요청 단락용. 없으면 null. */
    Order findByClientOrderId(@Param("clientOrderId") String clientOrderId);

    /** id+소유자로 조회 — 취소 권한 검증(타인 주문이면 null → 404). */
    Order findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 전이 가드 ② DB 레벨 — 현재 status가 expected일 때만 next로 전이하며 체결가 기록.
     * affected rows 반환(0이면 그 사이 상태가 바뀐 것 = 전이 실패).
     */
    int transitionFill(@Param("id") Long id, @Param("expected") OrderStatus expected,
                       @Param("next") OrderStatus next, @Param("price") BigDecimal price);

    /**
     * 취소 가드 — QUEUED/PENDING(취소 가능 상태)일 때만 CANCELLED. affected rows 반환.
     * 동시 체결·이중취소 경합을 조건부 UPDATE로 차단(0이면 이미 종결됨).
     */
    int cancelIfCancellable(@Param("id") Long id, @Param("userId") Long userId);

    /** 유저 거래내역(최신순) */
    List<Order> findByUserId(@Param("userId") Long userId);

    /** 유저 미체결 주문(최신순) — 온주 PENDING(지정가 대기) + 소수점 QUEUED(차수 대기). 종목 무관 전체. */
    List<Order> findActiveByUserId(@Param("userId") Long userId);

    /** 차수의 QUEUED 소수점 주문 전건 — 배치 집행 대상(#153). */
    List<Order> findQueuedByRound(@Param("roundId") Long roundId);

    /** 소수점 전이: QUEUED→SENT + batch_id 부착(전송, 취소 불가). 0행이면 경합(이미 취소/전이). */
    int sendForBatch(@Param("id") Long id, @Param("batchId") Long batchId);

    /** 소수점 전이: SENT→FILLED(배분 완료). 0행이면 정합성 오류(같은 tx라 항상 1). */
    int markFilledFractional(@Param("id") Long id);

    /** 소수점 전이: 활성(QUEUED/SENT)→REJECTED + 사유. 자금 원복과 함께 호출(FRAC-014). 0행이면 이미 종결. */
    int rejectActive(@Param("id") Long id, @Param("reason") String reason);

    /**
     * 지정가 미체결(PENDING) 주문을 거래소 집합으로 조회 — 매칭 엔진 부팅 재적재용(국내 한정).
     * SSOT=DB에서 인덱스(종목→PENDING)를 재구성한다.
     */
    List<Order> findPendingByExchanges(@Param("exchanges") Collection<String> exchanges);
}
