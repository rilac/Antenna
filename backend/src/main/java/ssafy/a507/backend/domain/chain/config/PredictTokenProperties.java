package ssafy.a507.backend.domain.chain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배포된 PredictToken(ANT) 컨트랙트의 위치 (ANT-CHAIN-03).
 *
 * <p>{@link CommitAnchorProperties} 와 같은 판단이다 — 주소는 배포마다 계산되는 값이라 환경변수로 받고,
 * <b>비어 있어도 앱이 떠야 한다.</b> 토큰을 배포하지 않은 팀원의 로컬이 부팅부터 실패하면 토큰과 무관한
 * 기능까지 개발을 못 한다. 실제로 tx 를 보내는 토큰 릴레이어(ANT-CHAIN-10)와 이벤트를 읽는 토큰
 * 인덱서(ANT-CHAIN-11)가 호출 직전에 {@link #isDeployed()} 로 막는다.
 *
 * <p>CommitAnchor 와 달리 이 주소는 <b>재배포로 바뀌지 않는 것을 전제</b>한다. 잔액이 이 주소에 살기 때문이다.
 * 기능 확장은 위성 컨트랙트 + MOVER_ROLE 로, 토큰 규칙 변경만 v2 이관이다({@code contracts/README.md}).
 */
@ConfigurationProperties(prefix = "app.chain.predict-token")
public record PredictTokenProperties(String address) {

    /** 0x + 40자 hex. */
    private static final int ADDRESS_LENGTH = 42;

    /**
     * 주소가 쓸 만한 형태로 설정돼 있는지. 길이까지 보는 이유는 {@link CommitAnchorProperties#isDeployed()} 와 같다 —
     * 잘린 주소로 mint 를 보내면 "없는 주소로 보낸 성공한 tx" 가 되어 실패도 안 나고 잔액도 안 는다.
     */
    public boolean isDeployed() {
        return address != null && address.length() == ADDRESS_LENGTH && address.startsWith("0x");
    }

    /** 소문자로 눕힌 주소. 비교·저장 형식을 {@code users.wallet_address} 와 맞춘다. */
    public String normalizedAddress() {
        return isDeployed() ? address.toLowerCase() : null;
    }
}
