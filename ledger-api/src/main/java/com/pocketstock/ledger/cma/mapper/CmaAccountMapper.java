package com.pocketstock.ledger.cma.mapper;

import com.pocketstock.ledger.cma.domain.CmaAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CmaAccountMapper {

    CmaAccount findByUserId(@Param("userId") Long userId);

    /** 잠금 읽기(FOR UPDATE) — 동시 개설 경합 시 커밋된 행을 확실히 조회(REPEATABLE READ 스냅샷 우회) */
    CmaAccount findByUserIdForUpdate(@Param("userId") Long userId);

    int insert(CmaAccount account);

    /** 로컬 시드 계좌번호 적재용 — account_no_enc를 현재 키 암호문으로 덮어쓴다(@Profile local 전용). */
    int updateAccountNoEnc(@Param("id") Long id, @Param("enc") byte[] enc);
}
