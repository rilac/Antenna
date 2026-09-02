package ssafy.a507.backend.domain.chain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배포된 CommitAnchor 컨트랙트의 위치 (ANT-CHAIN-01).
 *
 * <p>주소는 네트워크마다 다르고 재배포할 때마다 바뀐다. 컨트랙트 주소는 고를 수 있는 값이 아니라
 * {@code keccak256(rlp([배포자, nonce]))} 로 계산되는 값이라, 같은 코드를 다시 올려도 반드시 다른
 * 주소가 나온다. 그래서 코드에 못 박지 않고 환경변수로 받는다.
 *
 * <p><b>비어 있어도 앱이 떠야 한다.</b> 컨트랙트를 배포하지 않은 팀원의 로컬이 부팅부터 실패하면
 * 온체인과 무관한 기능까지 개발을 못 한다. SSAFY OAuth 자격증명이 없어도 서버가 뜨도록 해 둔
 * {@code app.auth.ssafy} 와 같은 판단이다. 실제로 tx 를 보내는 릴레이어(ANT-CHAIN-05)가
 * 호출 직전에 {@link #isDeployed()} 로 막는다.
 */
@ConfigurationProperties(prefix = "app.chain.commit-anchor")
public record CommitAnchorProperties(String address) {

    /** 0x + 40자 hex. */
    private static final int ADDRESS_LENGTH = 42;

    /**
     * 주소가 쓸 만한 형태로 설정돼 있는지.
     *
     * <p>길이까지 보는 이유: {@code .env} 에 주소를 붙여 넣다가 앞뒤가 잘리는 일이 실제로 흔하다.
     * 그 상태로 릴레이어가 tx 를 보내면 "없는 주소로 보낸 성공한 tx" 가 되고, 앵커는 안 됐는데
     * 실패도 안 나서 원인을 찾기 어렵다. 부팅 시점에 걸러 내는 편이 싸다.
     */
    public boolean isDeployed() {
        return address != null
                && address.length() == ADDRESS_LENGTH
                && address.startsWith("0x");
    }

    /** 소문자로 눕힌 주소. 비교·저장 형식을 {@code users.wallet_address} 와 맞춘다. */
    public String normalizedAddress() {
        return isDeployed() ? address.toLowerCase() : null;
    }
}
