package ssafy.a507.backend.domain.chain.relay;

import lombok.Getter;

/**
 * PredictToken 이 mint/burn/subscribe 를 거부했다 (ANT-CHAIN-10).
 *
 * <p>{@code ERC20InsufficientBalance}(→ INSUFFICIENT_BALANCE 409) 와 {@code AccessControlUnauthorizedAccount}
 * (→ CHAIN_UNAVAILABLE 503, 키 설정 문제) 는 여기로 오지 않는다 — 둘 다 사용자·운영자가 대응할 수 있는 실패라
 * {@code BusinessException} 으로 나간다. 여기 오는 건 {@code SelfSubscribe · ZeroAmount · ZeroAddress ·
 * SameAddress · TransferDisabled} 처럼 <b>호출자 검증을 뚫고 온 서버 버그</b>다. 다시 보내도 같으므로
 * 진입점은 FAILED 로 기록하고 500 을 낸다.
 */
@Getter
public class TokenRevertException extends RuntimeException {

    /** 컨트랙트 custom error 이름. 셀렉터를 못 알아보면 "Unknown(0x…)" */
    private final String errorName;

    public TokenRevertException(String errorName) {
        super("PredictToken revert: " + errorName);
        this.errorName = errorName;
    }
}
