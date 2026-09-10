package ssafy.a507.backend.domain.chain.relay;

import java.io.IOException;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.config.ChainProperties;

/**
 * 서버 키로 raw tx 를 보내는 유일한 창구 (ANT-CHAIN-10).
 *
 * <p>앵커 릴레이어와 토큰 릴레이어가 <b>같은 키</b>({@code RELAYER_PRIVATE_KEY} — CommitAnchor 의 ANCHOR_ROLE 이자
 * PredictToken 의 OPERATOR_ROLE)를 쓴다. web3j 의 {@link RawTransactionManager} 는 보낼 때마다
 * {@code eth_getTransactionCount(PENDING)} 으로 nonce 를 읽고 서명해 보내는데, 두 스레드가 동시에 그 셋을 하면
 * 같은 nonce 로 tx 둘을 만들어 한쪽이 "nonce too low" 로 거부된다. 앵커(00:05 배치 스레드)와 토큰 tx(요청 스레드)가
 * 겹칠 수 있고, 토큰 tx 끼리도 겹친다. 그래서 <b>조회 → 서명 → 전송을 한 락 안에</b> 둔다.
 *
 * <p>새 nonce 로직은 없다 — 락만 더했다. Besu 는 {@code pending} 태그에 풀에 있는 tx 까지 세고
 * {@code eth_sendRawTransaction} 은 풀에 넣은 뒤 돌아오므로, 락 안에서 순서대로 보내면 다음 조회가 N+1 을 준다
 * (실증: {@code Web3jTokenRelayerE2ELiveTest} — mint 둘을 receipt 없이 연달아). 로컬에 nonce 를 캐시하지 않는 이유는
 * 배포 스크립트·데모가 같은 키로 tx 를 보낼 때 캐시가 틀리기 때문이다.
 *
 * <p>receipt 대기는 여기 없다. 앵커는 락 밖에서 60초를 기다리고, 토큰은 아예 기다리지 않는다(확정은 인덱서).
 * 락이 receipt 까지 잡으면 앵커가 채굴되는 10초 동안 토큰 tx 가 전부 줄을 선다.
 *
 * <p>키가 없으면 {@link #isEnabled()} 가 false 고 릴레이어들이 그 앞에서 막는다 — 앱은 뜬다.
 */
@Slf4j
@Component
public class TxSender {

    private final ChainProperties props;
    private final ChainConnection connection;
    /** 키가 없으면 null. 릴레이어 둘이 {@link #isEnabled()} 로 먼저 막으므로 null 로 {@link #send} 에 오는 일은 없다. */
    private final Credentials credentials;
    /** 키 하나 = 락 하나. 키를 나누게 되면 주소별 맵으로 바꾼다 — 지금은 그럴 키가 없다. */
    private final Object lock = new Object();

    public TxSender(ChainProperties props, ChainConnection connection) {
        this.props = props;
        this.connection = connection;
        this.credentials =
                props.relayerEnabled() ? Credentials.create(props.relayer().privateKey()) : null;
    }

    /** RPC URL 과 키가 둘 다 있어야 보낼 수 있다. 컨트랙트 주소는 각 릴레이어가 따로 본다. */
    public boolean isEnabled() {
        return credentials != null && connection.isConfigured();
    }

    /** 서명 키의 주소. eth_call 의 from 과 로그에 쓴다. 키가 없으면 null. */
    public String senderAddress() {
        return credentials == null ? null : credentials.getAddress();
    }

    /**
     * nonce 조회 → 서명 → {@code eth_sendRawTransaction}. gasPrice 0 — 이 체인의 규칙이고, 넣지 않으면 EIP-1559 필드로 어긋난다.
     *
     * @return tx 해시. 노드가 tx 를 풀에 받았다는 뜻이지 채굴됐다는 뜻이 아니다
     * @throws IOException 소켓·전송 오류 — 호출자의 withReconnect 가 재연결 후 한 번 더 시도한다
     * @throws BusinessException CHAIN_UNAVAILABLE 노드가 tx 를 거부했다(nonce·가스·서명). 재시도는 호출자 정책
     */
    public String send(String to, String data, BigInteger gasLimit) throws IOException {
        synchronized (lock) {
            RawTransactionManager txm =
                    new RawTransactionManager(connection.web3j(), credentials, props.chainId());
            EthSendTransaction sent = txm.sendTransaction(BigInteger.ZERO, gasLimit, to, data, BigInteger.ZERO);
            if (sent.hasError()) {
                log.warn("tx 전송 거부({} → {}): {}", credentials.getAddress(), to, sent.getError().getMessage());
                throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
            }
            return sent.getTransactionHash();
        }
    }
}
