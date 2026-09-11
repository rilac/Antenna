package ssafy.a507.backend.domain.chain.relay;

import java.util.List;
import ssafy.a507.backend.common.error.BusinessException;

/**
 * CommitAnchor 전송기 (ANT-CHAIN-05).
 *
 * <p>앵커 배치(ANT-CHAIN-02)가 유일한 호출자다. 인터페이스로 뺀 이유는 테스트다 —
 * 배치의 상태 전이(PENDING → CONFIRMED / FAILED / 재시도)는 체인 없이 검증돼야 하고,
 * 그러려면 "확정됐다 / 이미 앵커됐다 / 타임아웃 / revert / RPC 장애" 를 마음대로 내는 가짜가 필요하다.
 *
 * <p>v3(ANT-CHAIN-13)부터 체인 쪽 칸의 키는 <b>머클루트</b>다. DB 배치 id 는 체인에 가지 않는다 —
 * DB 가 여러 개여도(로컬·운영·복원본) 번호가 부딪힐 일이 없다.
 */
public interface AnchorRelayer {

    /** 설정(RPC·키·주소)이 갖춰져 tx 를 보낼 수 있는지. 아니면 {@link #anchor} 는 CHAIN_UNAVAILABLE 을 던진다. */
    boolean isEnabled();

    /**
     * {@code anchor(merkleRoot, commitHashes)} 를 보낸다.
     *
     * @return CONFIRMED / ALREADY_ANCHORED / SENT_UNCONFIRMED — 호출자가 그대로 배치 상태로 옮긴다.
     *     ALREADY_ANCHORED 는 같은 루트 = 같은 내용이라 곧 성공이다
     * @throws AnchorRevertException 컨트랙트가 거부했다(RootMismatch 등). 배치 로직 버그라 재시도하지 않는다
     * @throws BusinessException CHAIN_UNAVAILABLE — RPC 에 닿지 못했다. 재시도 대상
     */
    AnchorResult anchor(byte[] merkleRoot, List<byte[]> commitHashes);

    /**
     * 온체인 {@code anchoredAt(merkleRoot)} — 그 루트가 박힌 블록 번호. 미앵커면 0.
     *
     * @throws BusinessException CHAIN_UNAVAILABLE
     */
    long anchoredAt(byte[] merkleRoot);
}
