package ssafy.a507.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.support.InMemorySignatureNonceStore;
import ssafy.a507.backend.support.WalletSignatures;

/**
 * 복원 경로만 보는 단위 테스트. 지갑도 체인도 DB도 필요 없다 —
 * userRepository는 recover()에서 쓰이지 않으므로 null로 둔다.
 */
@DisplayName("SignatureGuard — 서명에서 지갑 주소 복원")
class SignatureGuardTest {

    private static final long CHAIN_ID = 31337L;
    private static final String PRIVATE_KEY =
            "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final Long USER_ID = 1L;

    private Credentials credentials;
    private InMemorySignatureNonceStore nonceStore;
    private SignatureGuard signatureGuard;

    @BeforeEach
    void setUp() {
        credentials = Credentials.create(PRIVATE_KEY);
        nonceStore = new InMemorySignatureNonceStore();
        signatureGuard = new SignatureGuard(nonceStore, null, CHAIN_ID);
    }

    /** payload를 그대로 노출하는 최소 구현. 실제 DTO 대신 규격만 검증한다. */
    private record StubRequest(String signature, String payload) implements WalletSigned {
        @Override
        public SignatureScope scope() {
            return SignatureScope.WALLET_LINK;
        }

        @Override
        public String signingPayload(String nonce, long chainId) {
            return payload;
        }
    }

    @Test
    @DisplayName("서명한 개인키의 주소가 그대로 복원된다")
    void 주소_복원_성공() {
        String nonce = nonceStore.issue(USER_ID, SignatureScope.WALLET_LINK);
        String payload = "antenna:wallet-link:v1\nnonce=" + nonce;

        String recovered =
                signatureGuard.recover(
                        USER_ID,
                        new StubRequest(WalletSignatures.sign(payload, credentials), payload));

        assertThat(recovered).isEqualTo(credentials.getAddress());
    }

    @Test
    @DisplayName("복원 주소는 항상 0x + 소문자 40자다")
    void 복원_주소는_소문자다() {
        String payload = "antenna:test:v1";
        nonceStore.issue(USER_ID, SignatureScope.WALLET_LINK);

        String recovered =
                signatureGuard.recover(
                        USER_ID,
                        new StubRequest(WalletSignatures.sign(payload, credentials), payload));

        assertThat(recovered).matches("^0x[0-9a-f]{40}$");
    }

    @Test
    @DisplayName("v가 27/28 대신 0/1로 와도 보정해서 같은 주소를 복원한다")
    void v값_보정() {
        String payload = "antenna:test:v1";
        nonceStore.issue(USER_ID, SignatureScope.WALLET_LINK);
        String legacy = WalletSignatures.withLegacyV(WalletSignatures.sign(payload, credentials));

        String recovered = signatureGuard.recover(USER_ID, new StubRequest(legacy, payload));

        assertThat(recovered).isEqualTo(credentials.getAddress());
    }

    @Test
    @DisplayName("payload가 1바이트만 달라도 전혀 다른 주소가 나온다 — 예외가 아니라 불일치로 드러난다")
    void payload_변조는_다른_주소를_만든다() {
        nonceStore.issue(USER_ID, SignatureScope.WALLET_LINK);
        String signed = WalletSignatures.sign("publisher=7", credentials);

        String recovered = signatureGuard.recover(USER_ID, new StubRequest(signed, "publisher=99"));

        assertThat(recovered).isNotEqualTo(credentials.getAddress());
    }

    @Test
    @DisplayName("nonce를 한 번 쓰면 같은 요청을 다시 보낼 수 없다")
    void nonce는_1회용이다() {
        nonceStore.issue(USER_ID, SignatureScope.WALLET_LINK);
        String payload = "antenna:test:v1";
        StubRequest request =
                new StubRequest(WalletSignatures.sign(payload, credentials), payload);

        signatureGuard.recover(USER_ID, request);

        assertThatThrownBy(() -> signatureGuard.recover(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NONCE_NOT_FOUND);
    }

    @Test
    @DisplayName("서명이 65바이트가 아니면 INVALID_SIGNATURE")
    void 서명_길이가_틀리면_거절한다() {
        nonceStore.issue(USER_ID, SignatureScope.WALLET_LINK);

        assertThatThrownBy(
                        () ->
                                signatureGuard.recover(
                                        USER_ID, new StubRequest("0xdeadbeef", "antenna:test:v1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SIGNATURE);
    }
}
