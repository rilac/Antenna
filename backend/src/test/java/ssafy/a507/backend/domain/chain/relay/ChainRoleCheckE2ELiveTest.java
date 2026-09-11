package ssafy.a507.backend.domain.chain.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;

/**
 * ANT-CHAIN-12 — 키와 컨트랙트가 <b>환경 단위 짝</b>이라는 것을 실체인에서 확인한다. 평소 빌드에서는 돌지 않는다.
 *
 * <pre>
 * set -a; . backend/.env; set +a      # 개발 한 벌 — RELAYER_PRIVATE_KEY · CONTRACT_COMMIT_ANCHOR · CONTRACT_PREDICT_TOKEN
 * ./gradlew test --tests '*ChainRoleCheckE2ELiveTest*'
 * </pre>
 *
 * <p>읽기 전용(eth_call)이라 어느 배포본에도 흔적이 남지 않는다. 확인하는 것: 개발 릴레이어는 개발 컨트랙트의 역할이 있고,
 * 운영 컨트랙트(contracts/deployments/prod/)의 역할은 없다 — 그래서 로컬 노트북의 키로 운영을 건드릴 수 없다.
 */
@EnabledIfEnvironmentVariable(named = "CHAIN_RPC_URL", matches = "wss?://.+")
@EnabledIfEnvironmentVariable(named = "RELAYER_PRIVATE_KEY", matches = "(0x)?[0-9a-fA-F]{64}")
@EnabledIfEnvironmentVariable(named = "CONTRACT_COMMIT_ANCHOR", matches = "0x[0-9a-fA-F]{40}")
@EnabledIfEnvironmentVariable(named = "CONTRACT_PREDICT_TOKEN", matches = "0x[0-9a-fA-F]{40}")
@DisplayName("환경별 키·컨트랙트 짝 (SSAFY Live)")
class ChainRoleCheckE2ELiveTest {

    /** contracts/deployments/prod/CommitAnchor.json — 2026-09-11 배포. */
    private static final String PROD_COMMIT_ANCHOR = "0x8fDb4010b120DFf5c2d9b4c990821aeA00331C7e";
    /** contracts/deployments/prod/PredictToken.json — 2026-09-11 배포. */
    private static final String PROD_PREDICT_TOKEN = "0xEee56721cd2c0383139756c88B6DB06024a412Cb";

    private final ChainProperties props =
            new ChainProperties(
                    31221L,
                    System.getenv("CHAIN_RPC_URL"),
                    new ChainProperties.Relayer(System.getenv("RELAYER_PRIVATE_KEY")),
                    new ChainProperties.Anchor("-", 60, new ChainProperties.Anchor.Retry(3, 10)),
                    new ChainProperties.Indexer("-", 0, 10_000));
    private final ChainConnection connection = new ChainConnection(props);
    private final TxSender txSender = new TxSender(props, connection);
    private final ChainRoleCheck check =
            new ChainRoleCheck(
                    txSender,
                    connection,
                    new CommitAnchorProperties(System.getenv("CONTRACT_COMMIT_ANCHOR")),
                    new PredictTokenProperties(System.getenv("CONTRACT_PREDICT_TOKEN")));

    @Test
    @DisplayName("개발 릴레이어는 개발 CommitAnchor 의 ANCHOR_ROLE · 개발 PredictToken 의 OPERATOR_ROLE 을 갖는다")
    void 개발_짝은_역할이_있다() throws IOException {
        String relayer = txSender.senderAddress();

        assertThat(check.hasRole(System.getenv("CONTRACT_COMMIT_ANCHOR"), ChainRoleCheck.ANCHOR_ROLE, relayer)).isTrue();
        assertThat(check.hasRole(System.getenv("CONTRACT_PREDICT_TOKEN"), ChainRoleCheck.OPERATOR_ROLE, relayer)).isTrue();
    }

    @Test
    @DisplayName("개발 릴레이어는 운영 컨트랙트의 역할이 없다 — 환경끼리 섞어 쓸 수 없다")
    void 개발_키로는_운영을_못_움직인다() throws IOException {
        String relayer = txSender.senderAddress();

        assertThat(check.hasRole(PROD_COMMIT_ANCHOR, ChainRoleCheck.ANCHOR_ROLE, relayer)).isFalse();
        assertThat(check.hasRole(PROD_PREDICT_TOKEN, ChainRoleCheck.OPERATOR_ROLE, relayer)).isFalse();
    }
}
