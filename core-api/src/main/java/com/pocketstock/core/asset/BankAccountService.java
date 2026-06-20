package com.pocketstock.core.asset;

import com.pocketstock.core.asset.dto.BankAccountResponse;
import com.pocketstock.core.asset.mapper.BankAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountMapper bankAccountMapper;

    /** 유저 보유(연동) 은행 계좌 목록 조회 (조회 전용). */
    public List<BankAccountResponse> getBankAccounts(Long userId) {
        return bankAccountMapper.findBankAccountsByUser(userId);
    }
}
