package ssafy.a507.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.support.TestNonceStoreConfig;
import ssafy.a507.backend.support.WalletSignatures;

/**
 * verify()는 DB 대조까지 하므로 컨텍스트가 필요하다.
 * 온체인 동반 요청(PRED-01 · TOKEN-01 …)의 첫 컨트롤러가 아직 없어 모듈을 직접 호출한다.
 */
@SpringBootTest
@Import(TestNonceStoreConfig.class)
@Transactional
@DisplayName("SignatureGuard.verify — 연동 지갑과 대조")
class SignatureGuardVerifyTest {

    private static final String PRIVATE_KEY =
            "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String OTHER_PRIVATE_KEY =
            "0x8d5366123cb560bb606379f90a0bfd4769eecc0557f1b362dcae9012b548b1e5";

    @Autowired EntityManager em;
    @Autowired SignatureGuard signatureGuard;
    @Autowired SignatureNonceStore nonceStore;

    private Credentials credentials;

    @BeforeEach
    void setUp() {
        credentials = Credentials.create(PRIVATE_KEY);
    }

    /** 온체인 동반 요청 DTO를 흉내 낸다. 금액·대상이 payload에 들어가는 모양 그대로. */
    private record SubscribeStub(String signature, long publisherId) implements WalletSigned {
        @Override
        public String signingPayload(String nonce, long chainId) {
            return "antenna:subscribe:v1\npublisher="
                    + publisherId
                    + "\nchainId="
                    + chainId
                    + "\nnonce="
                    + nonce;
        }
    }

    private Long insertUser(String nickname, String walletAddress) {
        User user = User.create(nickname);
        if (walletAddress != null) {
            user.linkWallet(walletAddress);
        }
        em.persist(user);
        em.flush();
        return user.getId();
    }

    private SubscribeStub signed(Long userId, String nonce, Credentials signer, long publisherId) {
        String payload = new SubscribeStub("", publisherId).signingPayload(nonce, 31337L);
        return new SubscribeStub(WalletSignatures.sign(payload, signer), publisherId);
    }

    @Test
    @DisplayName("연동 지갑으로 서명하면 통과한다")
    void 서명자_일치() {
        Long userId = insertUser("연동한사람", credentials.getAddress());
        String nonce = nonceStore.issue(userId);

        String recovered = signatureGuard.verify(userId, signed(userId, nonce, credentials, 7L));

        assertThat(recovered).isEqualTo(credentials.getAddress());
    }

    @Test
    @DisplayName("지갑 미연동이면 WALLET_NOT_LINKED — UNAUTHORIZED로 뭉개지지 않는다")
    void 미연동_계정은_연동을_유도한다() {
        Long userId = insertUser("미연동사람", null);
        nonceStore.issue(userId);

        assertThatThrownBy(
                        () -> signatureGuard.verify(userId, signed(userId, "unused", credentials, 7L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_NOT_LINKED);
    }

    @Test
    @DisplayName("미연동으로 거절될 때는 nonce를 태우지 않는다")
    void 실패해도_nonce는_남는다() {
        Long userId = insertUser("미연동사람2", null);
        String nonce = nonceStore.issue(userId);

        assertThatThrownBy(
                        () -> signatureGuard.verify(userId, signed(userId, nonce, credentials, 7L)))
                .isInstanceOf(BusinessException.class);

        assertThat(nonceStore.consume(userId)).isEqualTo(nonce);
    }

    @Test
    @DisplayName("다른 지갑으로 서명하면 401 SIGNER_MISMATCH")
    void 서명자_불일치() {
        Long userId = insertUser("연동한사람2", credentials.getAddress());
        String nonce = nonceStore.issue(userId);

        assertThatThrownBy(
                        () ->
                                signatureGuard.verify(
                                        userId,
                                        signed(
                                                userId,
                                                nonce,
                                                Credentials.create(OTHER_PRIVATE_KEY),
                                                7L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIGNER_MISMATCH);
    }

    @Test
    @DisplayName("body의 publisher를 바꿔치기하면 복원 주소가 달라져 401")
    void body_변조는_401로_드러난다() {
        Long userId = insertUser("연동한사람3", credentials.getAddress());
        String nonce = nonceStore.issue(userId);
        // publisher=7로 서명해 두고, 서버에는 publisher=99인 요청으로 보낸다.
        String tampered = signed(userId, nonce, credentials, 7L).signature();

        assertThatThrownBy(
                        () -> signatureGuard.verify(userId, new SubscribeStub(tampered, 99L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIGNER_MISMATCH);
    }
}
