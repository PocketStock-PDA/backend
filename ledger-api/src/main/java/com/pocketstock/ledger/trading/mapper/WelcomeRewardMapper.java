package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.WelcomeReward;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WelcomeRewardMapper {

    /** 지급 이력 INSERT (useGeneratedKeys → id 채움) */
    int insert(WelcomeReward reward);

    /** 이미 지급받았는지(1인 1회 검증) */
    boolean existsByUserId(@Param("userId") Long userId);

    /** 유저 지급 내역(최신순) */
    List<WelcomeReward> findByUserId(@Param("userId") Long userId);
}
