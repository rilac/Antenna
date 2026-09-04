package ssafy.a507.backend.domain.chain.relay;

/**
 * 앵커 전송 결과 (ANT-CHAIN-05).
 *
 * <p>revert 와 RPC 장애는 예외로 나가고, 여기엔 "tx 가 나간 뒤" 의 세 갈래만 담는다.
 *
 * @param status      아래 셋 중 하나
 * @param txHash      보낸 tx. ALREADY_ANCHORED 면 null — 이번엔 안 보냈다
 * @param blockNumber receipt 의 블록. CONFIRMED 일 때만
 */
public record AnchorResult(Status status, String txHash, Long blockNumber) {

    public enum Status {
        /** receipt status=1. 배치를 CONFIRMED 로 옮긴다. */
        CONFIRMED,
        /** 전송 전 시뮬레이션이 BatchAlreadyAnchored 로 막혔다 = 이전 실행이 이미 성공했다. 배치를 CONFIRMED 로 옮긴다. */
        ALREADY_ANCHORED,
        /** tx 는 나갔는데 receipt 를 제한 시간 안에 못 받았다. PENDING + sent_at 으로 두고 다음 실행이 rootOf 로 확인한다. */
        SENT_UNCONFIRMED
    }

    public static AnchorResult confirmed(String txHash, long blockNumber) {
        return new AnchorResult(Status.CONFIRMED, txHash, blockNumber);
    }

    public static AnchorResult alreadyAnchored() {
        return new AnchorResult(Status.ALREADY_ANCHORED, null, null);
    }

    public static AnchorResult sentUnconfirmed(String txHash) {
        return new AnchorResult(Status.SENT_UNCONFIRMED, txHash, null);
    }
}
