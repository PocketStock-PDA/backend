package com.pocketstock.user.member;

import com.pocketstock.common.exception.BusinessException;
import com.pocketstock.common.exception.ErrorCode;
import com.pocketstock.user.member.domain.Member;
import com.pocketstock.user.member.dto.PasswordValidateResponse;
import com.pocketstock.user.member.dto.SignupRequest;
import com.pocketstock.user.member.dto.SignupResponse;
import com.pocketstock.user.member.dto.UsernameCheckResponse;
import com.pocketstock.user.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("uuMMdd");

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 아이디 중복 확인.
     * 빈 입력은 잘못된 요청(예외), '중복'은 에러가 아니라 정상 응답(available=false).
     */
    public UsernameCheckResponse checkUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "username은 필수입니다.");
        }

        int count = memberMapper.countByUsername(username);
        return new UsernameCheckResponse(count == 0);
    }

    /**
     * 비밀번호 보안규칙 실시간 검증.
     * 입력 단계 피드백용이라 규칙 위반을 예외로 던지지 않고 valid/failedRules로 응답한다.
     */
    public PasswordValidateResponse validatePassword(String password) {
        List<String> failedRules = PasswordPolicy.validate(password);
        return new PasswordValidateResponse(failedRules.isEmpty(), failedRules);
    }

    /** 회원가입. 중복 아이디는 409(CONFLICT). */
    @Transactional
    public SignupResponse signup(SignupRequest req) {
        validate(req);

        if (memberMapper.countByUsername(req.username()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        Member member = Member.builder()
                .username(req.username())
                .passwordHash(passwordEncoder.encode(req.password()))  // BCrypt 단방향 해싱
                .name(req.name())
                .phone(req.phone())
                .birthDate(parseBirthDate(req.residentFront(), req.residentBack()))
                .gender(parseGender(req.residentBack()))
                .build();

        try {
            memberMapper.insertMember(member);   // 이후 member.getId()에 PK가 채워짐
        } catch (DataIntegrityViolationException e) {
            // 사전 체크와 INSERT 사이 동시 가입(username UNIQUE 제약 위반) → 500 대신 409
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        return new SignupResponse(member.getId(), member.getUsername());
    }

    private void validate(SignupRequest req) {
        if (!StringUtils.hasText(req.username())
                || !StringUtils.hasText(req.password())
                || !StringUtils.hasText(req.name())
                || !StringUtils.hasText(req.phone())
                || !StringUtils.hasText(req.residentFront())
                || !StringUtils.hasText(req.residentBack())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "필수 항목이 누락되었습니다.");
        }
        if (!PasswordPolicy.isValid(req.password())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호가 보안 규칙을 충족하지 않습니다.");
        }
        if (req.residentFront().length() != 6 || req.residentBack().length() != 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주민번호 형식이 올바르지 않습니다.");
        }
    }

    /** 앞 6자리(YYMMDD) + 뒤 1자리(세기·성별 코드)로 실제 생년월일 계산. */
    private LocalDate parseBirthDate(String front, String back) {
        int code = back.charAt(0) - '0';
        int century = switch (code) {
            case 1, 2, 5, 6 -> 1900;
            case 3, 4, 7, 8 -> 2000;
            case 9, 0 -> 1800;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "주민번호 뒷자리가 올바르지 않습니다.");
        };
        try {
            LocalDate yymmdd = LocalDate.parse(front, YYMMDD);  // 1900~1999로 일단 파싱
            return yymmdd.withYear(century + (yymmdd.getYear() % 100));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "생년월일이 올바르지 않습니다.");
        }
    }

    /** 뒤 1자리가 홀수면 남성, 짝수면 여성. */
    private String parseGender(String back) {
        return (back.charAt(0) - '0') % 2 == 1 ? "MALE" : "FEMALE";
    }
}
