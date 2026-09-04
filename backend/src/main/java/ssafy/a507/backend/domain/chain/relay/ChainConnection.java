package ssafy.a507.backend.domain.chain.relay;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.ConnectException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.config.ChainProperties;

/**
 * 웹소켓 RPC 연결 (ANT-CHAIN-05).
 *
 * <p><b>지연 연결.</b> 부팅 때 붙지 않는다. SSAFY 노드가 잠깐 죽어 있어도 서버는 떠야 하고,
 * 처음 쓰는 곳(하루 한 번 앵커 배치)이 붙이면 된다.
 *
 * <p><b>재연결은 "실패하면 버리고 새로 붙인다"</b> 가 전부다. web3j 의 {@code WebSocketService} 는
 * 끊긴 뒤 스스로 재연결하지 않고, 끊긴 소켓으로 요청하면 {@code WebsocketNotConnectedException} 이나
 * {@code IOException} 이 난다. 그걸 잡아 {@link #reset()} 하고 호출자가 한 번 더 시도한다.
 * 상시 구독(인덱서, ANT-CHAIN-04)이 붙을 때 재연결 정책을 다시 본다 — 지금은 하루 한 번 쓰는 클라이언트다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(ChainProperties.class)
public class ChainConnection {

    private final ChainProperties props;
    private WebSocketService service;
    private Web3j web3j;

    public ChainConnection(ChainProperties props) {
        this.props = props;
    }

    public boolean isConfigured() {
        return props.hasRpcUrl();
    }

    /** 연결된 클라이언트. 없으면 붙이고, 못 붙으면 CHAIN_UNAVAILABLE. */
    public synchronized Web3j web3j() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
        }
        if (web3j != null) {
            return web3j;
        }
        try {
            // includeRawResponses=false. wss 라 TLS 는 Java-WebSocket 이 알아서 한다.
            WebSocketService ws = new WebSocketService(props.rpcUrl(), false);
            ws.connect();
            this.service = ws;
            this.web3j = Web3j.build(ws);
            log.info("체인 RPC 연결: {}", props.rpcUrl());
            return web3j;
        } catch (ConnectException e) {
            throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
        } catch (RuntimeException e) {
            // Java-WebSocket 은 URI 오류·핸드셰이크 실패를 런타임 예외로 낸다.
            log.warn("체인 RPC 연결 실패: {}", e.toString());
            throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
        }
    }

    /** 끊긴 소켓을 버린다. 다음 {@link #web3j()} 가 새로 붙인다. */
    public synchronized void reset() {
        if (service != null) {
            try {
                service.close();
            } catch (RuntimeException ignored) {
                // 이미 닫힌 소켓을 또 닫는 예외. 버리는 중이라 상관없다.
            }
        }
        service = null;
        web3j = null;
    }

    /** {@link IOException} 이나 소켓 예외면 리셋하고 true — 호출자는 한 번 더 시도한다. */
    public boolean resetIfConnectionError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof IOException || cur.getClass().getName().contains("WebsocketNotConnected")) {
                log.warn("체인 RPC 연결 오류, 재연결 예정: {}", cur.toString());
                reset();
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    @PreDestroy
    void shutdown() {
        reset();
    }
}
