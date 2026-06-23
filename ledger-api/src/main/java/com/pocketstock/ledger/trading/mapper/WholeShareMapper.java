package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.WholeShareEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WholeShareMapper {

    /** 온주 전환 이벤트 INSERT. */
    int insert(WholeShareEvent event);

    /** 유저 온주 전환내역(최신순) — 종목명 join. */
    List<WholeShareEvent> findByUserId(@Param("userId") Long userId);
}
