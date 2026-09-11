package ssafy.a507.backend.domain.chain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 체인 접속과 앵커 배치 설정 (ANT-CHAIN-05 · ANT-CHAIN-02).
 *
 * <p>{@link CommitAnchorProperties}(컨트랙트 주소)와 같은 {@code app.chain} 접두사 아래 산다.
 * 주소는 CHAIN-01 이 먼저 만든 record 라 그대로 두고, 나머지를 여기서 받는다.
 *
 * <p><b>비어 있어도 앱이 떠야 한다.</b> RPC URL 이나 릴레이어 키가 없으면 릴레이어만 꺼지고
 * (`{@link #relayerEnabled}` = false) 앵커 배치는 실행마다 로그 한 줄 남기고 건너뛴다.
 * 컨트랙트를 배포하지 않은 팀원의 로컬이 부팅부터 실패하면 안 된다 — 주소·SSAFY OAuth 와 같은 판단.
 *
 * @param chainId  서명 payload 와 tx 서명에 박히는 체인 ID. 원천은 env {@code CHAIN_ID} 하나이고
 *     {@code SignatureGuard} 도 같은 env 를 {@code @Value} 로 읽는다. 읽는 곳이 둘이지만 값은 하나다.
 * @param rpcUrl   웹소켓 RPC. SSAFY 는 wss 뿐이다(HTTP 미확인).
 * @param relayer  앵커 tx 를 보낼 서버 키. ANCHOR_ROLE 만 가진다(관리자 키는 서버 밖).
 * @param anchor   앵커 배치 주기·재시도.
 * @param indexer  앵커 인덱서(ANT-CHAIN-04) 폴링 주기·시작 블록·구간 상한.
 */
@ConfigurationProperties(prefix = "app.chain")
public record ChainProperties(long chainId, String rpcUrl, Relayer relayer, Anchor anchor, Indexer indexer) {

    public record Relayer(String privateKey) {
        public boolean isConfigured() {
            return privateKey != null && !privateKey.isBlank();
        }
    }

    /**
     * @param cron                  매일 00:05 KST 기본. 테스트는 "-" 로 끈다
     * @param receiptTimeoutSeconds receipt 대기 상한. 넘기면 "전송됨·미확정"으로 두고 다음 실행이 anchoredAt 으로 확인한다
     * @param retry                 실행 안 즉시 재시도(RPC 장애만). revert 는 재시도하지 않는다
     */
    public record Anchor(String cron, int receiptTimeoutSeconds, Retry retry) {
        public record Retry(int count, int delaySeconds) {}
    }

    /**
     * @param cron          기본 5초(`*&#47;5 * * * * *`). 테스트는 "-" 로 끈다
     * @param fromBlock     DB 에 이벤트가 하나도 없을 때의 시작 블록. 0 이면 제네시스부터 — SSAFY 에서도 전 구간
     *     getLogs 가 되지만(검증정보 §3.1) 배포 블록을 주면 첫 동기화가 한 회차로 끝난다
     * @param maxBlockRange getLogs 한 번에 물어보는 블록 수 상한. 첫 동기화·장기 정지 후 복구가 이 단위로 쪼개진다
     */
    public record Indexer(String cron, long fromBlock, int maxBlockRange) {}

    public boolean hasRpcUrl() {
        return rpcUrl != null && !rpcUrl.isBlank();
    }

    /** RPC 와 키가 둘 다 있어야 tx 를 보낼 수 있다. 주소는 {@link CommitAnchorProperties#isDeployed()} 가 따로 본다. */
    public boolean relayerEnabled() {
        return hasRpcUrl() && relayer != null && relayer.isConfigured();
    }
}
