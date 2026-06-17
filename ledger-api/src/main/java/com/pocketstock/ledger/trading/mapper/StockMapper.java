package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.TradableStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockMapper {

    /** 종목명/코드/영문명 부분일치 검색(활성 종목만). 정확·접두 일치 우선 정렬. */
    List<TradableStock> search(@Param("keyword") String keyword, @Param("limit") int limit);

    /** 단축코드로 단건 조회(활성 여부 무관 — 보유/주문 검증에 재사용 가능). */
    TradableStock findByCode(@Param("stockCode") String stockCode);
}
