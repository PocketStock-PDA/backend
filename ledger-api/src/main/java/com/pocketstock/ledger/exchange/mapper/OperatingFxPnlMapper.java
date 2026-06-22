package com.pocketstock.ledger.exchange.mapper;

import com.pocketstock.ledger.exchange.domain.OperatingFxPnl;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperatingFxPnlMapper {

    /** 회사 환차익 1줄 INSERT (불변 journal, append-only). idempotency_key UNIQUE로 재적재 중복 차단. */
    int insert(OperatingFxPnl pnl);
}
