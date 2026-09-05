package ssafy.a507.backend.domain.chain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

/**
 * GET /api/v1/predictions/{id}/proof 200 응답 — 3단계 검산 재료 일체 (ANT-CHAIN-06, 화면 D-03).
 *
 * <p>서버는 검증하지 않는다. ① {@code hash(payload ‖ salt) == commitHash} ② proof 로 루트 복원 → 체인 {@code rootOf(batchId)}
 * 대조 ③ 판정 종가 ↔ 공공데이터 원본, 셋 다 <b>브라우저가 실행</b>한다. 이 응답은 그 계산의 입력값이다.
 *
 * <p>명세의 평면 필드(merkleProof·merkleRoot·anchorTxHash·anchorBlockNumber)를 {@link Anchor} 하나로 묶었다(09-06 결정).
 * 앵커 전에는 넷이 각각 null 이 아니라 {@code anchor == null} + {@link #anchorStatus} 하나로 "대기" 를 말한다 —
 * 프론트 AnchorBadge 의 입력이 상태값 하나면 된다.
 *
 * <p>비공개 값 두 가지의 규칙이 다르다. {@code salt} 는 리빌 뒤 <b>회원 전원</b>에게 간다 — 비구독자도 ①을 검산해야 한다.
 * {@code noteSalt} 는 리빌 뒤에도 <b>작성자·구독자만</b> 받는다(결정 D6) — 근거 본문은 판정 후에도 구독자 전용인데
 * {@code noteHash} 가 공개되면 짧은 근거를 후보 해시로 맞출 수 있어서다. 컬럼·계산은 PRED-02 몫이라 지금은 자리만 있다.
 */
public record ProofResponse(
        long predictionId,
        Payload payload,
        String commitHash,
        String signature,
        String signerAddress,
        Instant revealedAt,
        String salt,
        String noteSalt,
        Anchor anchor,
        AnchorStatus anchorStatus,
        Settle settle) {

    /** 커밋 배치 상태 + "아직 배치에 안 들어감"(WAITING). 배치 상태 셋은 {@link AnchorBatch.Status} 와 이름이 같다. */
    public enum AnchorStatus {
        WAITING,
        PENDING,
        CONFIRMED,
        FAILED;

        public static AnchorStatus of(AnchorBatch batch) {
            return batch == null ? WAITING : valueOf(batch.getStatus().name());
        }
    }

    /**
     * 커밋 payload 의 구성 필드. 문자열 조립 규격(줄 구분, 결정 B2)은 PRED-02 가 정한다 — 여기서는 값만 내린다.
     * {@code noteHash} 는 PRED-02 가 {@code keccak256(note ‖ noteSalt)} 로 계산해 저장하기 전까지 null 이다.
     */
    public record Payload(
            String stockCode,
            Prediction.Direction direction,
            BigDecimal targetPrice,
            short horizon,
            String noteHash,
            Instant createdAt) {}

    /**
     * 배치와 이 커밋의 자리. {@code merkleProof} 는 아래에서 위로 형제 해시(정렬 결합이라 좌우 정보 없음).
     * {@code batchId · contractAddress · chainId} 가 브라우저 {@code rootOf} 호출의 인자다(결정 F1) — 재배포 뒤에도
     * 이 배치는 <b>이 주소</b>의 장부에서 찾아야 한다.
     */
    public record Anchor(
            long batchId,
            String merkleRoot,
            List<String> merkleProof,
            int leafIndex,
            int leafCount,
            AnchorBatch.Status status,
            String txHash,
            Long blockNumber,
            Instant confirmedAt,
            String contractAddress,
            long chainId) {}

    /**
     * 판정 결과. HIT/MISS 뒤에만 채운다. {@code sourceUrl} 은 공공데이터포털의 같은 종목·날짜 조회 주소 — 인증키는 붙이지 않으니
     * 사용자가 자기 키로 열어 종가를 대조한다(③).
     */
    public record Settle(
            Prediction.Status status,
            LocalDate settleDate,
            BigDecimal settlePrice,
            BigDecimal basePrice,
            BigDecimal errorRate,
            String sourceUrl) {}
}
