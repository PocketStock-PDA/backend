package com.pocketstock.ledger.outbox;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OutboxMapper {

    /** 이벤트 기록 — 발행원 트랜잭션과 같은 커밋. event_id UNIQUE라 중복 기록 시 DuplicateKey(멱등). */
    int insert(Outbox outbox);

    /** 미발행 이벤트 배치 조회(id asc) — 릴레이 폴링. */
    List<Outbox> findUnpublished(@Param("limit") int limit);

    /**
     * 발행 완료 마킹 — 행 선점(멀티 인스턴스 이중발행 차단). published=false였던 것만 true로(affected=1).
     * @return 1=이 호출이 선점·발행 확정, 0=다른 인스턴스가 이미 발행.
     */
    int markPublished(@Param("id") Long id);

    /** 최근 이벤트 N건(id desc) — dev 검증용. */
    List<Outbox> findRecent(@Param("limit") int limit);
}
