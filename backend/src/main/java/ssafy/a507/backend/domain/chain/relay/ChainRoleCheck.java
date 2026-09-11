package ssafy.a507.backend.domain.chain.relay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;

/**
 * 부팅 직후 "릴레이어 키가 설정된 컨트랙트의 역할을 실제로 갖는가" 를 체인에서 읽어 로그로 남긴다 (ANT-CHAIN-12).
 *
 * <p>같은 SSAFY 체인에 dev · prod 두 벌의 컨트랙트와 키가 산다(contracts/deployments/README.md). 역할은 주소에
 * 부여되므로 한 환경의 키로 다른 환경의 컨트랙트를 부르면 시뮬레이션에서 {@code AccessControlUnauthorizedAccount} 가 난다.
 * 지금까지는 그게 00:05 첫 앵커나 첫 결제에서야 드러났다. 이 컴포넌트가 배포 직후 로그 한 번으로 당긴다.
 *
 * <p><b>부팅을 막지 않는다.</b> RPC 가 잠깐 죽어 있다고 운영 서버 전체가 안 뜨면 안 된다 — "체인 설정이 비어도 앱은 뜬다"
 * ({@link ssafy.a507.backend.domain.chain.config.ChainProperties}) 와 같은 판단이다. 결과는 로그뿐이고 동작은 안 바꾼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainRoleCheck {

    /** keccak256("ANCHOR_ROLE") — CommitAnchor.sol 의 상수. view 호출로 읽지 않고 여기서 계산한다(왕복 하나 절약). */
    static final String ANCHOR_ROLE = Hash.sha3String("ANCHOR_ROLE");
    /** keccak256("OPERATOR_ROLE") — PredictToken.sol 의 상수. */
    static final String OPERATOR_ROLE = Hash.sha3String("OPERATOR_ROLE");

    private final TxSender txSender;
    private final ChainConnection connection;
    private final CommitAnchorProperties anchor;
    private final PredictTokenProperties token;

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        // 키나 RPC 가 없으면 릴레이어들이 이미 "꺼짐" 로그를 남겼다. 확인할 짝이 없다.
        if (!txSender.isEnabled()) {
            return;
        }
        List<Target> targets = new ArrayList<>();
        if (anchor.isDeployed()) {
            targets.add(new Target("CommitAnchor", anchor.normalizedAddress(), "ANCHOR_ROLE", ANCHOR_ROLE));
        }
        if (token.isDeployed()) {
            targets.add(new Target("PredictToken", token.normalizedAddress(), "OPERATOR_ROLE", OPERATOR_ROLE));
        }
        if (targets.isEmpty()) {
            return;
        }

        String relayer = txSender.senderAddress();
        StringBuilder report = new StringBuilder("체인 환경 확인 — 릴레이어 ").append(relayer);
        boolean allOk = true;
        for (Target t : targets) {
            boolean ok;
            try {
                ok = hasRole(t.address(), t.roleHash(), relayer);
            } catch (IOException | RuntimeException e) {
                log.warn("체인 환경 확인 건너뜀 — {} 조회 실패({}). 다음 부팅에서 다시 본다", t.name(), e.toString());
                return;
            }
            allOk &= ok;
            report.append("\n  ")
                    .append(t.name()).append(' ').append(t.address()).append(' ').append(t.roleName())
                    .append(ok ? " ✓" : " ✗ 없음");
        }
        if (allOk) {
            log.info(report.toString());
        } else {
            // 주소에 코드가 없는 경우(엉뚱한 주소)도 hasRole 이 빈 응답이라 ✗ 로 나온다 — 대응은 같다.
            log.error(
                    "{}\n  → 이 키로는 ✗ 컨트랙트에 tx 를 못 보낸다. RELAYER_PRIVATE_KEY 의 주소와"
                            + " contracts/deployments/<env>/*.json 의 relayer · operator 가 같은 환경인지 대조하라",
                    report);
        }
    }

    /** {@code hasRole(bytes32,address)} calldata. 셀렉터 0x91d14854. */
    static String hasRoleCall(String roleHash, String account) {
        return FunctionEncoder.encode(hasRoleFunction(roleHash, account));
    }

    /** 패키지 공개 — {@code ChainRoleCheckE2ELiveTest} 가 환경끼리 섞이지 않는다는 것을 실체인에서 확인할 때 쓴다. */
    boolean hasRole(String contract, String roleHash, String account) throws IOException {
        Function fn = hasRoleFunction(roleHash, account);
        EthCall call =
                connection
                        .web3j()
                        .ethCall(
                                Transaction.createEthCallTransaction(account, contract, FunctionEncoder.encode(fn)),
                                DefaultBlockParameterName.LATEST)
                        .send();
        if (call.hasError() || call.isReverted()) {
            throw new IOException("hasRole eth_call 실패: " + (call.hasError() ? call.getError().getMessage() : "revert"));
        }
        List<Type> out = FunctionReturnDecoder.decode(call.getValue(), fn.getOutputParameters());
        return !out.isEmpty() && ((Bool) out.get(0)).getValue();
    }

    private static Function hasRoleFunction(String roleHash, String account) {
        return new Function(
                "hasRole",
                List.of(new Bytes32(Numeric.hexStringToByteArray(roleHash)), new Address(account)),
                List.of(new TypeReference<Bool>() {}));
    }

    private record Target(String name, String address, String roleName, String roleHash) {}
}
