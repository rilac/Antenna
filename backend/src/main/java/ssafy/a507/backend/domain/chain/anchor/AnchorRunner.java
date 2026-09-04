package ssafy.a507.backend.domain.chain.anchor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.relay.AnchorRelayer;
import ssafy.a507.backend.domain.chain.relay.AnchorResult;
import ssafy.a507.backend.domain.chain.relay.AnchorRevertException;

/**
 * 일일 앵커 실행 (ANT-CHAIN-02). 스케줄러가 하루 한 번 부른다.
 *
 * <pre>
 * ① 재시도  FAILED · 보냈는데 미확정인 PENDING → rootOf ≠ 0 이면 CONFIRMED, 아니면 같은 id 로 재전송
 * ② 신규    anchor_batch_id IS NULL 인 커밋 전부 → 배치 하나 → 전송 (0건이면 아무것도 안 함)
 * ③ 리빌    판정(HIT/MISS)이 끝난 커밋의 salt 공개
 * </pre>
 *
 * <p>트랜잭션은 {@link AnchorBatchService} 메서드 단위이고, 체인 호출은 그 사이에서 한다.
 *
 * <p>즉시 재시도는 <b>RPC 장애(CHAIN_UNAVAILABLE)에만</b> 한다. revert 는 다시 보내도 같으므로 바로 FAILED.
 * 무한 재시도를 안 하는 이유: RPC 가 몇 시간 죽어 있으면 스케줄러 스레드가 붙잡힌다. 3회 뒤 FAILED 로
 * 두고 다음 날 실행이 이어받는다(결정 A2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnchorRunner {

    private static final byte[] ZERO32 = new byte[32];

    private final AnchorBatchService service;
    private final AnchorRelayer relayer;
    private final SaltRevealService reveal;
    private final ChainProperties props;

    /** @return 이번 실행이 만든 새 배치 id (없으면 empty) */
    public Optional<Long> run(LocalDate businessDate) {
        if (!relayer.isEnabled()) {
            // 리빌은 체인이 필요 없다. 릴레이어가 꺼져 있어도 salt 공개는 돈다.
            log.info("앵커 배치 건너뜀 — 릴레이어가 꺼져 있다(RPC·키·주소 확인). 리빌만 실행");
            reveal.revealSettled(Instant.now());
            return Optional.empty();
        }

        for (Long id : service.findRetryTargets()) {
            retry(id);
        }

        Optional<Long> created =
                service.openBatch(businessDate)
                        .map(
                                p -> {
                                    log.info(
                                            "앵커 배치 #{} 생성: {}건, root {}",
                                            p.batchId(),
                                            p.commitHashes().size(),
                                            hex(p.merkleRoot()));
                                    send(p);
                                    return p.batchId();
                                });

        reveal.revealSettled(Instant.now());
        return created;
    }

    private void retry(long batchId) {
        // 보낸 적 있는 배치는 체인을 먼저 본다 — 이미 박혀 있으면 재전송이 BatchAlreadyAnchored 로 막히지만,
        // 그 전에 알 수 있는 걸 굳이 tx 로 확인할 이유가 없다.
        if (service.wasSent(batchId)) {
            try {
                byte[] onchain = relayer.rootOf(batchId);
                if (!Arrays.equals(onchain, ZERO32)) {
                    service.confirmByRootCheck(batchId, Instant.now());
                    log.info("앵커 배치 #{} — rootOf 확인으로 CONFIRMED", batchId);
                    return;
                }
            } catch (BusinessException e) {
                log.warn("앵커 배치 #{} rootOf 조회 실패({}) — 이번 실행은 건너뜀", batchId, e.getErrorCode());
                return;
            }
        }
        log.info("앵커 배치 #{} 재전송", batchId);
        send(service.payloadOf(batchId));
    }

    private void send(AnchorBatchService.Payload p) {
        int max = Math.max(1, props.anchor().retry().count());
        for (int attempt = 1; attempt <= max; attempt++) {
            service.markSending(p.batchId());
            try {
                AnchorResult result = relayer.anchor(p.batchId(), p.merkleRoot(), p.commitHashes());
                service.recordResult(p.batchId(), result, Instant.now());
                log.info(
                        "앵커 배치 #{} → {} tx={} block={}",
                        p.batchId(),
                        result.status(),
                        result.txHash(),
                        result.blockNumber());
                return;
            } catch (AnchorRevertException e) {
                // 다시 보내도 같은 결과. 배치 로직 버그 신호다.
                service.markFailed(p.batchId(), e.getErrorName());
                log.error("앵커 배치 #{} revert: {} — 재시도하지 않음", p.batchId(), e.getErrorName());
                return;
            } catch (BusinessException e) {
                if (e.getErrorCode() != ErrorCode.CHAIN_UNAVAILABLE) {
                    service.markFailed(p.batchId(), e.getErrorCode().name());
                    throw e;
                }
                service.markFailed(p.batchId(), "CHAIN_UNAVAILABLE");
                log.warn("앵커 배치 #{} 전송 실패 {}/{} — RPC 장애", p.batchId(), attempt, max);
                if (attempt < max) {
                    sleep(props.anchor().retry().delaySeconds());
                }
            } catch (RuntimeException e) {
                service.markFailed(p.batchId(), e.getClass().getSimpleName() + ": " + e.getMessage());
                log.error("앵커 배치 #{} 예기치 않은 실패", p.batchId(), e);
                return;
            }
        }
    }

    private static void sleep(int seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder("0x");
        for (int i = 0; i < Math.min(8, b.length); i++) {
            sb.append(String.format("%02x", b[i]));
        }
        return sb.append('…').toString();
    }
}
