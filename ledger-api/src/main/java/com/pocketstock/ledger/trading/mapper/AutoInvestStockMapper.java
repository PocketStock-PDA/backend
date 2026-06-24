package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.AutoInvestStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AutoInvestStockMapper {

    /** 종목별 자동모으기 등록. (user_id, stock_code) UNIQUE — 중복 시 DuplicateKey. */
    int insert(AutoInvestStock stock);

    /** 유저의 자동모으기 종목 전체(최신순) — 종목명 join. */
    List<AutoInvestStock> findByUserId(@Param("userId") Long userId);

    /** 단건 상세 — 남의 설정 노출 금지(user_id 가드). */
    AutoInvestStock findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 설정 수정(주기·금액·수량) — user_id 가드. stock_code/market은 불변. */
    int update(AutoInvestStock stock);

    /** 종목 ON/OFF(일시중지·재개) — user_id 가드. */
    int updateActive(@Param("id") Long id, @Param("userId") Long userId, @Param("active") boolean active);

    /** 해제(완전 삭제) — user_id 가드. 트리거·회차로그는 FK CASCADE로 함께 삭제. */
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
