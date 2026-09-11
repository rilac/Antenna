package ssafy.a507.backend.domain.chain.relay;

import lombok.Getter;

/**
 * 컨트랙트가 anchor 를 거부했다 (ANT-CHAIN-05).
 *
 * <p>{@code AlreadyAnchored} 는 여기로 오지 않는다 — 그건 "이미 성공" 이라 {@link AnchorResult} 로 돌아간다.
 * 여기 오는 건 {@code RootMismatch} · {@code EmptyRoot} · {@code EmptyCommitCount} ·
 * {@code AccessControlUnauthorizedAccount} 처럼 <b>다시 보내도 같은 결과</b>인 것들이다. 배치 로직이나 키 설정의
 * 버그이므로 호출자는 FAILED 로 기록하고 즉시 재시도하지 않는다.
 */
@Getter
public class AnchorRevertException extends RuntimeException {

    /** 컨트랙트 custom error 이름. 셀렉터를 못 알아보면 "Unknown(0x…)" */
    private final String errorName;

    public AnchorRevertException(String errorName) {
        super("CommitAnchor revert: " + errorName);
        this.errorName = errorName;
    }
}
