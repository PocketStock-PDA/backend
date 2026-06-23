package com.pocketstock.ledger.trading.mapper;

import com.pocketstock.ledger.trading.domain.SecuritiesAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SecuritiesAccountMapper {

    /** 개설(useGeneratedKeys → id 채움) */
    int insert(SecuritiesAccount account);

    /** 유저의 전체 계좌(개설 순) */
    List<SecuritiesAccount> findByUserId(@Param("userId") Long userId);

    /** 시장별 존재 여부 */
    boolean existsByUserIdAndMarket(@Param("userId") Long userId, @Param("market") String market);

    /** 유저의 특정 시장 계좌 1건 (없으면 null) */
    SecuritiesAccount findByUserIdAndMarket(@Param("userId") Long userId, @Param("market") String market);

    /** 로컬 시드 계좌번호 적재용 — account_no_enc를 현재 키 암호문으로 덮어쓴다(@Profile local 전용). */
    int updateAccountNoEnc(@Param("id") Long id, @Param("enc") byte[] enc);
}
