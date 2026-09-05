package ssafy.a507.backend.domain.chain.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;
import ssafy.a507.backend.domain.chain.entity.ChainEvent;
import ssafy.a507.backend.domain.chain.repository.AnchorBatchRepository;
import ssafy.a507.backend.domain.chain.repository.ChainEventRepository;

/**
 * ANT-CHAIN-04 — 인덱서의 커서·멱등·배치 반영·정지. 체인은 {@link FakeChainLogSource} 가 대신한다.
 *
 * <p>H2 다. append-only 트리거(plpgsql)는 여기서 돌지 않는다 — {@code processed_at} 만 UPDATE 하는지는
 * {@code @DynamicUpdate} 와 PostgreSQL 실기(진행상황.md 체크리스트)로 본다.
 */
// 인덱서는 RPC URL + 컨트랙트 주소가 있어야 켜진다. 실제 소켓은 FakeChainLogSource(@Primary) 가 대신하므로 URL 은 아무 값이다.
// 릴레이어 키는 비어 있어 릴레이어는 꺼진 채다 — 인덱서가 키 없이 도는 것도 검증 대상이다.
@SpringBootTest(
        properties = {
            "app.chain.rpc-url=wss://fake.invalid",
            "app.chain.commit-anchor.address=0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D6a",
            "app.chain.indexer.from-block=100",
            "app.chain.indexer.max-block-range=50"
        })
@Import(FakeChainLogSource.Config.class)
@Transactional
@DisplayName("앵커 인덱서")
class AnchorIndexerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 4);
    private static final String ROOT_A = FakeLogs.keccak("root-a");
    private static final String ROOT_B = FakeLogs.keccak("root-b");

    @Autowired EntityManager em;
    @Autowired AnchorIndexer indexer;
    @Autowired FakeChainLogSource source;
    @Autowired AnchorBatchRepository batches;
    @Autowired ChainEventRepository events;

    @BeforeEach
    void setUp() {
        source.reset();
        // 인덱서는 싱글턴이라 메모리 커서·정지 플래그가 테스트 사이에 남는다. 새 인스턴스 상태로 되돌린다.
        resetIndexer();
    }

    // ── 배치 반영 ────────────────────────────────────────────────────

    @Test
    @DisplayName("보냈는데 미확정(PENDING+sent_at)인 배치는 이벤트로 CONFIRMED 되고 tx·블록이 채워진다")
    void 미확정_배치를_이벤트로_확정() {
        AnchorBatch b = sentBatch(ROOT_A, "0xdead");
        source.add(FakeLogs.anchored(b.getId(), ROOT_A, 120));

        indexer.poll();

        AnchorBatch after = reload(b);
        assertThat(after.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(after.getTxHash()).isEqualTo(FakeLogs.keccak("tx-" + b.getId()));
        assertThat(after.getBlockNumber()).isEqualTo(120L);
        assertThat(after.getConfirmedAt()).isNotNull();
        assertThat(after.getLastError()).isNull();

        List<ChainEvent> saved = events.findAll();
        assertThat(saved).hasSize(1);
        ChainEvent e = saved.get(0);
        assertThat(e.getEventName()).isEqualTo("Anchored");
        assertThat(e.getBlockNumber()).isEqualTo(120L);
        assertThat(e.getContractAddress()).isEqualTo(FakeLogs.CONTRACT);
        assertThat(e.getProcessedAt()).isNotNull();
        assertThat(e.getPayload()).contains("\"batchId\":" + b.getId()).contains(ROOT_A).contains(FakeLogs.blockHashOf(120));
        assertThat(indexer.scannedUpTo()).contains(120L);
    }

    @Test
    @DisplayName("릴레이어가 이미 CONFIRMED 로 찍은 배치는 confirmed_at 이 그대로다 — 이벤트는 저장만")
    void 이미_확정된_배치는_시각을_건드리지_않는다() {
        AnchorBatch b = sentBatch(ROOT_A, FakeLogs.keccak("tx-99"));
        Instant confirmedAt = Instant.parse("2026-09-04T00:05:30Z");
        reload(b).markConfirmed(FakeLogs.keccak("tx-99"), 120L, confirmedAt); // 관리 엔티티에 찍어야 DB 에 간다
        em.flush();
        em.clear();
        source.add(FakeLogs.anchored(b.getId(), ROOT_A, 120));

        indexer.poll();

        AnchorBatch after = reload(b);
        assertThat(after.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(after.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("rootOf 로만 확인돼 tx·블록이 NULL 인 CONFIRMED 배치는 이벤트에서 채워진다")
    void rootOf로_확인된_배치의_참조를_채운다() {
        AnchorBatch b = sentBatch(ROOT_A, "0xold");
        Instant confirmedAt = Instant.parse("2026-09-05T00:05:30Z");
        reload(b).markConfirmed(null, null, confirmedAt); // confirmByRootCheck 와 같다 — 알던 옛 해시는 남는다
        em.flush();
        em.clear();
        source.add(FakeLogs.anchored(b.getId(), ROOT_A, 130));

        indexer.poll();

        AnchorBatch after = reload(b);
        assertThat(after.getTxHash()).isEqualTo(FakeLogs.keccak("tx-" + b.getId())); // 체인이 진실 — 옛 해시를 덮는다
        assertThat(after.getBlockNumber()).isEqualTo(130L);
        assertThat(after.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    @DisplayName("이벤트의 루트가 우리 루트와 다르면 BATCH_ID_COLLISION 으로 FAILED — 절대 CONFIRMED 아님")
    void 루트_불일치는_충돌_실패() {
        AnchorBatch b = sentBatch(ROOT_A, "0xdead");
        source.add(FakeLogs.anchored(b.getId(), ROOT_B, 120));

        indexer.poll();

        AnchorBatch after = reload(b);
        assertThat(after.getStatus()).isEqualTo(AnchorBatch.Status.FAILED);
        assertThat(after.getLastError()).startsWith("BATCH_ID_COLLISION");
        assertThat(after.getConfirmedAt()).isNull();
        assertThat(events.count()).isEqualTo(1); // 이벤트는 그래도 남는다
        assertThat(events.findAll().get(0).getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("DB 에 없는 batchId 는 이벤트만 저장·처리 완료로 두고 배치는 만들지 않는다")
    void 모르는_batchId는_이벤트만_남긴다() {
        source.add(FakeLogs.anchored(777, ROOT_A, 120));

        indexer.poll();

        assertThat(batches.count()).isZero();
        assertThat(events.count()).isEqualTo(1);
        assertThat(events.findAll().get(0).getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 컨트랙트가 낸 같은 batchId 는 배치를 건드리지 않는다 (재배포 전 배치)")
    void 다른_컨트랙트의_이벤트는_무시() {
        AnchorBatch b = sentBatch(ROOT_A, "0xdead", "0x1111111111111111111111111111111111111111");
        source.add(FakeLogs.anchored(b.getId(), ROOT_B, 120)); // 현재 컨트랙트가 낸 이벤트 — 루트가 달라도 남의 배치다

        indexer.poll();

        AnchorBatch after = reload(b);
        assertThat(after.getStatus()).isEqualTo(AnchorBatch.Status.PENDING);
        assertThat(after.getLastError()).isNull();
        assertThat(events.count()).isEqualTo(1);
    }

    // ── 멱등 · 커서 ─────────────────────────────────────────────────

    @Test
    @DisplayName("같은 로그를 두 회차에 걸쳐 받아도 chain_events 는 한 행이다")
    void 중복_로그는_한_번만() {
        AnchorBatch b = sentBatch(ROOT_A, "0xdead");
        source.add(FakeLogs.anchored(b.getId(), ROOT_A, 120));

        indexer.poll();
        resetIndexer(); // 재시작 — 메모리 커서가 사라져 DB 커서(120)부터 다시 훑는다
        source.head(125);
        indexer.poll();

        assertThat(events.count()).isEqualTo(1);
        assertThat(reload(b).getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
    }

    @Test
    @DisplayName("첫 회차는 from-block(100)부터, 두 번째 회차는 메모리 커서 다음 블록부터 묻는다")
    void 커서는_훑은_다음_블록부터() {
        source.head(160);

        indexer.poll();
        source.head(170);
        indexer.poll();

        // max-block-range=50: [100,149] [150,160] 그리고 [161,170]
        assertThat(source.ranges())
                .containsExactly(
                        new FakeChainLogSource.Range(100, 149),
                        new FakeChainLogSource.Range(150, 160),
                        new FakeChainLogSource.Range(161, 170));
        assertThat(indexer.scannedUpTo()).contains(170L);
    }

    @Test
    @DisplayName("재시작 후 DB 커서(max block_number)가 from-block 보다 크면 그 다음부터 시작한다")
    void 재시작은_DB_커서에서() {
        source.add(FakeLogs.anchored(777, ROOT_A, 140));
        indexer.poll();
        source.ranges().clear();

        resetIndexer();
        source.head(145);
        indexer.poll();

        assertThat(source.ranges()).containsExactly(new FakeChainLogSource.Range(141, 145));
    }

    @Test
    @DisplayName("헤드가 커서보다 뒤면 아무것도 묻지 않는다")
    void 새_블록이_없으면_조용하다() {
        source.head(160);
        indexer.poll();
        source.ranges().clear();

        indexer.poll(); // head 그대로 160

        assertThat(source.ranges()).isEmpty();
    }

    // ── 재처리 · 정지 · 장애 ────────────────────────────────────────

    @Test
    @DisplayName("processed_at IS NULL 인 행은 다음 회차가 payload 로 다시 반영한다 (운영자 수동 복구 경로)")
    void 미처리_행_재처리() {
        AnchorBatch b = sentBatch(ROOT_A, "0xdead");
        String payload =
                "{\"batchId\":" + b.getId() + ",\"merkleRoot\":\"" + ROOT_A + "\",\"commitHashes\":[],\"blockHash\":\""
                        + FakeLogs.blockHashOf(120) + "\"}";
        em.persist(ChainEvent.record(FakeLogs.keccak("tx-manual"), 0, FakeLogs.CONTRACT, "Anchored", 120L, payload));
        em.flush();
        em.clear();
        source.head(120);

        indexer.poll();

        AnchorBatch after = reload(b);
        assertThat(after.getStatus()).isEqualTo(AnchorBatch.Status.CONFIRMED);
        assertThat(after.getTxHash()).isEqualTo(FakeLogs.keccak("tx-manual"));
        assertThat(events.findAll().get(0).getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("마지막 이벤트 블록의 해시가 체인과 다르면 정지 — 이후 회차는 아무것도 묻지 않는다")
    void reorg면_정지() {
        source.add(FakeLogs.anchored(777, ROOT_A, 120));
        indexer.poll();
        assertThat(indexer.isHalted()).isFalse();

        source.overrideBlockHash(120, FakeLogs.keccak("some-other-block"));
        source.head(130);
        source.ranges().clear();
        indexer.poll();

        assertThat(indexer.isHalted()).isTrue();
        assertThat(source.ranges()).isEmpty();

        source.ranges().clear();
        indexer.poll();
        assertThat(source.ranges()).isEmpty();
        assertThat(source.latestBlockCalls()).isEqualTo(1); // 첫 회차 한 번뿐
    }

    @Test
    @DisplayName("기록한 블록이 체인에 없어도(짧아짐) 정지")
    void 블록이_사라져도_정지() {
        source.add(FakeLogs.anchored(777, ROOT_A, 120));
        indexer.poll();

        source.overrideBlockHash(120, null);
        indexer.poll();

        assertThat(indexer.isHalted()).isTrue();
    }

    @Test
    @DisplayName("RPC 장애 회차는 건너뛰고 정지하지 않는다. 복구되면 이어서 훑는다")
    void RPC_장애는_건너뛰고_이어간다() {
        source.head(160);
        indexer.poll();
        source.unavailable(true);
        source.head(170);
        indexer.poll();
        assertThat(indexer.isHalted()).isFalse();
        assertThat(indexer.scannedUpTo()).contains(160L);

        source.unavailable(false);
        source.ranges().clear();
        indexer.poll();

        assertThat(source.ranges()).containsExactly(new FakeChainLogSource.Range(161, 170));
    }

    // ── 픽스처 ──────────────────────────────────────────────────────

    private AnchorBatch sentBatch(String root, String txHash) {
        return sentBatch(root, txHash, FakeLogs.CONTRACT);
    }

    /** "보냈는데 receipt 를 못 받은" 배치 — PENDING + sent_at + tx_hash. */
    private AnchorBatch sentBatch(String root, String txHash, String contract) {
        AnchorBatch b = AnchorBatch.open(DAY, root, 2, contract, 31337L);
        b.markSending();
        b.markSent(txHash, Instant.parse("2026-09-04T15:05:00Z"));
        em.persist(b);
        em.flush();
        em.clear();
        return b;
    }

    private AnchorBatch reload(AnchorBatch b) {
        em.flush();
        em.clear();
        return batches.findById(b.getId()).orElseThrow();
    }

    /** 싱글턴 인덱서의 메모리 상태(커서·정지)를 재시작 직후로. 리플렉션 대신 전용 메서드를 쓴다. */
    private void resetIndexer() {
        indexer.resetForTest();
    }
}
