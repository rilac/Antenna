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
 * ANT-CHAIN-12 — env 의 릴레이어 키와 컨트랙트 주소가 <b>짝</b>인지 실체인에서 확인한다. 평소 빌드에서는 돌지 않는다.
 *
 * <pre>
 * # 로컬 Hardhat 체인에 돌릴 때(contracts/deployments/README.md "로컬")
 * CHAIN_RPC_URL=ws://127.0.0.1:8545 RELAYER_PRIVATE_KEY=0x… CONTRACT_COMMIT_ANCHOR=0x… CONTRACT_PREDICT_TOKEN=0x… \
 * ./gradlew test --tests '*ChainRoleCheckE2ELiveTest*'
 * </pre>
 *
 * <p>읽기 전용(eth_call)이라 어느 체인에도 흔적이 남지 않는다. 역할은 주소에 주어지므로, 이게 ✓ 가 아니면 그 키로는 그 컨트랙트에
 * tx 를 못 보낸다 — 서버 부팅 로그 {@code 체인 환경 확인} 과 같은 판정을 테스트로 돌린다.
 *
 * <p>09-11 오후엔 "개발 키 → 운영 컨트랙트 ✗" 도 확인했다(2/2 통과). 같은 날 저녁 개발 세트를 폐기해 그 단언은 뺐다.
 */
@EnabledIfEnvironmentVariable(named = "CHAIN_RPC_URL", matches = "wss?://.+")
@EnabledIfEnvironmentVariable(named = "RELAYER_PRIVATE_KEY", matches = "(0x)?[0-9a-fA-F]{64}")
@EnabledIfEnvironmentVariable(named = "CONTRACT_COMMIT_ANCHOR", matches = "0x[0-9a-fA-F]{40}")
@EnabledIfEnvironmentVariable(named = "CONTRACT_PREDICT_TOKEN", matches = "0x[0-9a-fA-F]{40}")
@DisplayName("env 의 키·컨트랙트 짝 (Live)")
class ChainRoleCheckE2ELiveTest {

    private final ChainProperties props =
            new ChainProperties(
                    Long.parseLong(System.getenv().getOrDefault("CHAIN_ID", "31221")),
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
    @DisplayName("릴레이어 키가 CommitAnchor 의 ANCHOR_ROLE · PredictToken 의 OPERATOR_ROLE 을 갖는다")
    void 짝이_맞으면_역할이_있다() throws IOException {
        String relayer = txSender.senderAddress();

        assertThat(check.hasRole(System.getenv("CONTRACT_COMMIT_ANCHOR"), ChainRoleCheck.ANCHOR_ROLE, relayer)).isTrue();
        assertThat(check.hasRole(System.getenv("CONTRACT_PREDICT_TOKEN"), ChainRoleCheck.OPERATOR_ROLE, relayer)).isTrue();
    }
}
