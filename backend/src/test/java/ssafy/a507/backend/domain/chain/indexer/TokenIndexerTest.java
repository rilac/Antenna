package ssafy.a507.backend.domain.chain.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;
import ssafy.a507.backend.domain.chain.entity.ChainEvent;
import ssafy.a507.backend.domain.chain.entity.Operation;
import ssafy.a507.backend.domain.chain.entity.TokenLedger;
import ssafy.a507.backend.domain.chain.relay.TokenReason;
import ssafy.a507.backend.domain.chain.repository.ChainEventRepository;
import ssafy.a507.backend.domain.chain.repository.OperationRepository;
import ssafy.a507.backend.domain.monetize.entity.AdBanner;
import ssafy.a507.backend.domain.monetize.entity.Subscription;

/**
 * ANT-CHAIN-11 — 토큰 인덱서 ② 의 반영·정리·커서. 체인은 {@link FakeChainLogSource} 가 대신한다. H2 다.
 *
 * <p>앵커 주소는 비워 앵커 인덱서는 꺼진 채다 — 둘이 독립인 것도 검증 대상이다. 시간 문턱(30초·10분)은
 * {@code poll(Instant)} 에 미래 시각을 넣어 민다.
 */
@SpringBootTest(
        properties = {
            "app.chain.rpc-url=wss://fake.invalid",
            "app.chain.predict-token.address=0xe11d728b157240DCf4a8c831176D248BAFD33077",
            "app.chain.indexer.from-block=100",
            "app.chain.indexer.max-block-range=50"
        })
@Import(FakeChainLogSource.Config.class)
@Transactional
@DisplayName("토큰 인덱서 ②")
class TokenIndexerTest {

    static final String TOKEN = "0xe11d728b157240dcf4a8c831176d248bafd33077";
    static final String ANCHOR = "0x07f8cfe2bc6174d62be8226e5e8699ffbf0d6d6a";
    static final String WALLET_A = "0x00000000000000000000000000000000000000aa";
    static final String WALLET_B = "0x00000000000000000000000000000000000000bb";
    static final String STRANGER = "0x00000000000000000000000000000000000000ee";

    @Autowired EntityManager em;
    @Autowired TokenIndexer indexer;
    @Autowired TokenIndexService service;
    @Autowired FakeChainLogSource source;
    @Autowired ChainEventRepository events;
    @Autowired OperationRepository operations;
    @Autowired ChainProperties props;

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        source.reset();
        indexer.resetForTest();
    }

    // ── 반영 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Minted → 회원 원장 +amount(reason), 같은 tx 의 작업은 SUCCEEDED, 이벤트는 processed")
    void minted() {
        User a = user(WALLET_A);
        String tx = tx("m1");
        Operation op = op(a, Operation.Kind.SEASON_JOIN, null, null, tx);
        source.add(minted(tx, 120, WALLET_A, 1000, TokenReason.SEASON_REWARD));

        indexer.poll(now);

        assertThat(ledger(a)).singleElement().satisfies(l -> {
            assertThat(l.getDelta()).isEqualTo(BigInteger.valueOf(1000));
            assertThat(l.getReason()).isEqualTo("SEASON_REWARD");
            assertThat(l.getChainEvent().getTxHash()).isEqualTo(tx);
        });
        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.SUCCEEDED);
        assertThat(reload(op).getSettledAt()).isNotNull();
        assertThat(events.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getEventName()).isEqualTo("Minted");
            assertThat(e.getContractAddress()).isEqualTo(TOKEN);
            assertThat(e.isProcessed()).isTrue();
        });
    }

    @Test
    @DisplayName("Burned → 원장 −amount")
    void burned() {
        User a = user(WALLET_A);
        String tx = tx("b1");
        op(a, Operation.Kind.PREDICTION_BURN, null, null, tx);
        source.add(burned(tx, 121, WALLET_A, 2000, TokenReason.SLOT_OVER));

        indexer.poll(now);

        assertThat(ledger(a)).singleElement().satisfies(l -> {
            assertThat(l.getDelta()).isEqualTo(BigInteger.valueOf(-2000));
            assertThat(l.getReason()).isEqualTo("SLOT_OVER");
        });
    }

    @Test
    @DisplayName("Subscribed → 구독자 −amount(SUBSCRIBE) · 예측가 +creatorShare(SUBSCRIBE_INCOME) · 구독 ACTIVE +30일 · tx_hash")
    void subscribed() {
        User sub = user(WALLET_A);
        User creator = user(WALLET_B);
        Long subId = pendingSubscription(sub, creator);
        String tx = tx("s1");
        Operation op = op(sub, Operation.Kind.SUBSCRIBE, Operation.ResourceType.SUBSCRIPTION, subId, tx);
        source.add(subscribed(tx, 122, WALLET_A, WALLET_B, 30_000, 21_000, 9_000));

        indexer.poll(now);

        assertThat(ledger(sub)).singleElement().satisfies(l -> {
            assertThat(l.getDelta()).isEqualTo(BigInteger.valueOf(-30_000));
            assertThat(l.getReason()).isEqualTo("SUBSCRIBE");
        });
        assertThat(ledger(creator)).singleElement().satisfies(l -> {
            assertThat(l.getDelta()).isEqualTo(BigInteger.valueOf(21_000));
            assertThat(l.getReason()).isEqualTo("SUBSCRIBE_INCOME");
        });
        Subscription s = em.find(Subscription.class, subId);
        assertThat(s.getStatus()).isEqualTo(Subscription.Status.ACTIVE);
        assertThat(s.getTxHash()).isEqualTo(tx);
        assertThat(s.getStartedAt()).isEqualTo(now);
        assertThat(s.getExpiresAt()).isEqualTo(now.plus(30, ChronoUnit.DAYS));
        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.SUCCEEDED);
    }

    @Test
    @DisplayName("광고 소각 확정 → AdBanner ACTIVE + tx_hash (소유자가 열어 둔 activate)")
    void ad_activated() {
        User a = user(WALLET_A);
        AdBanner banner = pendingBanner(a);
        String tx = tx("ad1");
        op(a, Operation.Kind.AD, Operation.ResourceType.AD, banner.getId(), tx);
        source.add(burned(tx, 123, WALLET_A, 5, TokenReason.AD_PAY));

        indexer.poll(now);

        assertThat(banner.getStatus()).isEqualTo(AdBanner.Status.ACTIVE);
        assertThat(banner.getTxHash()).isEqualTo(tx);
    }

    @Test
    @DisplayName("회원이 아닌 지갑의 이벤트는 저장·processed 만 — 원장 없음, 작업 없음")
    void stranger_wallet() {
        String tx = tx("x1");
        source.add(minted(tx, 124, STRANGER, 1000, TokenReason.SIGNUP_BONUS));

        indexer.poll(now);

        assertThat(events.findAll()).singleElement().satisfies(e -> assertThat(e.isProcessed()).isTrue());
        assertThat(em.createQuery("select count(l) from TokenLedger l", Long.class).getSingleResult()).isZero();
    }

    @Test
    @DisplayName("enum 에 없는 reason(MIGRATION) 은 원문 그대로 원장에 남는다")
    void unknown_reason() {
        User a = user(WALLET_A);
        source.add(new TokenLog(tx("mig"), 0, 125, FakeLogs.blockHashOf(125), TOKEN, TokenLog.Kind.MINTED, null, WALLET_A,
                BigInteger.valueOf(7), null, null, "0x4d4947524154494f4e" + "00".repeat(23)));

        indexer.poll(now);

        assertThat(ledger(a)).singleElement().satisfies(l -> assertThat(l.getReason()).isEqualTo("MIGRATION"));
    }

    @Test
    @DisplayName("같은 로그를 두 회차에 걸쳐 받아도 chain_events·원장은 한 행이다")
    void idempotent() {
        User a = user(WALLET_A);
        source.add(minted(tx("d1"), 126, WALLET_A, 10, TokenReason.SIGNUP_BONUS));

        indexer.poll(now);
        indexer.resetForTest(); // 재시작 — DB 커서부터 다시 훑는다
        indexer.poll(now);

        assertThat(events.count()).isEqualTo(1);
        assertThat(ledger(a)).hasSize(1);
    }

    @Test
    @DisplayName("이미 FAILED(SEND_LOST) 인 작업에 이벤트가 오면 — 작업은 그대로, 원장만 쓴다. 자동 환불 없음(유저 결정)")
    void event_after_send_lost() {
        User a = user(WALLET_A);
        String tx = tx("late");
        Operation op = op(a, Operation.Kind.PREDICTION_BURN, null, null, null);
        op.markFailed("SEND_LOST", "test");
        em.flush();
        source.add(burned(tx, 127, WALLET_A, 2000, TokenReason.SLOT_OVER));

        indexer.poll(now);

        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.FAILED);
        assertThat(ledger(a)).singleElement().satisfies(l -> assertThat(l.getDelta()).isEqualTo(BigInteger.valueOf(-2000)));
    }

    // ── 이벤트 없는 실패 정리 ─────────────────────────────────────────────

    @Test
    @DisplayName("tx_hash 없는 PENDING 은 10분 뒤 SEND_LOST 로 FAILED. 그 전엔 그대로")
    void send_lost() {
        User a = user(WALLET_A);
        Operation op = op(a, Operation.Kind.PREDICTION_BURN, null, null, null);

        indexer.poll(now.plus(9, ChronoUnit.MINUTES));
        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.PENDING);

        indexer.poll(now.plus(11, ChronoUnit.MINUTES));
        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.FAILED);
        assertThat(reload(op).getErrorCode()).isEqualTo("SEND_LOST");
        assertThat(source.receiptCalls()).isZero(); // tx 가 없으니 receipt 를 물을 것도 없다
    }

    @Test
    @DisplayName("receipt status 0 → REVERTED. 30초 전에는 receipt 를 묻지 않는다. 광고면 배너 REJECTED")
    void reverted() {
        User a = user(WALLET_A);
        AdBanner banner = pendingBanner(a);
        String tx = tx("rv");
        Operation op = op(a, Operation.Kind.AD, Operation.ResourceType.AD, banner.getId(), tx);
        source.receipt(tx, false);

        indexer.poll(now.plus(10, ChronoUnit.SECONDS));
        assertThat(source.receiptCalls()).isZero();
        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.PENDING);

        indexer.poll(now.plus(31, ChronoUnit.SECONDS));
        assertThat(source.receiptCalls()).isEqualTo(1);
        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.FAILED);
        assertThat(reload(op).getErrorCode()).isEqualTo("REVERTED");
        assertThat(banner.getStatus()).isEqualTo(AdBanner.Status.REJECTED);
    }

    @Test
    @DisplayName("receipt 가 10분 안에 안 나오면 TX_DROPPED. 그 전엔 PENDING")
    void dropped() {
        User a = user(WALLET_A);
        Operation op = op(a, Operation.Kind.PREDICTION_BURN, null, null, tx("gone"));

        indexer.poll(now.plus(5, ChronoUnit.MINUTES));
        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.PENDING);

        indexer.poll(now.plus(11, ChronoUnit.MINUTES));
        assertThat(reload(op).getErrorCode()).isEqualTo("TX_DROPPED");
    }

    @Test
    @DisplayName("receipt 는 성공인데 이벤트가 아직 없으면 그대로 둔다 — 인덱서 지연. 다음 회차에 이벤트가 오면 SUCCEEDED")
    void receipt_ok_event_late() {
        User a = user(WALLET_A);
        String tx = tx("late-ok");
        Operation op = op(a, Operation.Kind.PREDICTION_BURN, null, null, tx);
        source.receipt(tx, true);

        indexer.poll(now.plus(11, ChronoUnit.MINUTES));
        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.PENDING);

        source.add(burned(tx, 130, WALLET_A, 2000, TokenReason.SLOT_OVER));
        indexer.poll(now.plus(12, ChronoUnit.MINUTES));
        assertThat(reload(op).getStatus()).isEqualTo(Operation.Status.SUCCEEDED);
    }

    // ── 커서 · 활성 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("커서는 컨트랙트별 — 앵커 이벤트가 블록 300 에 있어도 토큰 커서는 from-block(100)부터")
    void cursor_independent() {
        events.save(ChainEvent.record(tx("anc"), 0, ANCHOR, "Anchored", 300, "{\"batchId\":1,\"merkleRoot\":\"0x00\",\"commitHashes\":[],\"blockHash\":\"0x00\"}"));
        source.head(320);

        indexer.poll(now);

        assertThat(source.tokenRanges().get(0).from()).isEqualTo(100);
        assertThat(indexer.scannedUpTo()).contains(320L);
    }

    @Test
    @DisplayName("토큰 주소가 없으면 꺼진다 — 릴레이어 키는 안 본다")
    void disabled_without_address() {
        TokenIndexer off = new TokenIndexer(source, service, props, new PredictTokenProperties(null));
        assertThat(off.isEnabled()).isFalse();
        assertThat(indexer.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("마지막 토큰 이벤트 블록의 해시가 체인과 다르면 정지 — 이후 회차는 아무것도 묻지 않는다")
    void reorg_halts() {
        user(WALLET_A);
        source.add(minted(tx("r1"), 140, WALLET_A, 1, TokenReason.SIGNUP_BONUS));
        indexer.poll(now);
        assertThat(indexer.isHalted()).isFalse();

        source.overrideBlockHash(140, FakeLogs.keccak("other-chain"));
        indexer.resetForTest();
        indexer.poll(now);

        assertThat(indexer.isHalted()).isTrue();
        int ranges = source.tokenRanges().size();
        indexer.poll(now);
        assertThat(source.tokenRanges()).hasSize(ranges);
    }

    // ── 픽스처 ────────────────────────────────────────────────────────────

    private User user(String wallet) {
        User u = User.create();
        u.linkWallet(wallet);
        em.persist(u);
        return u;
    }

    private Operation op(User user, Operation.Kind kind, Operation.ResourceType type, Long resourceId, String txHash) {
        Operation op = Operation.accept(user, kind, type, resourceId);
        if (txHash != null) {
            op.markSent(txHash);
        }
        em.persist(op);
        em.flush();
        return op;
    }

    private Operation reload(Operation op) {
        em.flush();
        return operations.findById(op.getId()).orElseThrow();
    }

    private List<TokenLedger> ledger(User user) {
        return em.createQuery("select l from TokenLedger l where l.user.id = :id order by l.id", TokenLedger.class)
                .setParameter("id", user.getId())
                .getResultList();
    }

    private Long pendingSubscription(User subscriber, User publisher) {
        em.createNativeQuery(
                        "INSERT INTO subscriptions (subscriber_id, publisher_id, fee, status, auto_renew, created_at, updated_at) "
                                + "VALUES (:s, :p, 30000, 'PENDING', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .setParameter("s", subscriber.getId())
                .setParameter("p", publisher.getId())
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT max(id) FROM subscriptions").getSingleResult()).longValue();
    }

    private AdBanner pendingBanner(User advertiser) {
        AdBanner b = AdBanner.request(advertiser, "http://img", "http://link", now, now.plus(1, ChronoUnit.DAYS), BigInteger.valueOf(5));
        em.persist(b);
        em.flush();
        return b;
    }

    static String tx(String seed) {
        return FakeLogs.keccak("token-tx-" + seed);
    }

    static TokenLog minted(String tx, long block, String to, long amount, TokenReason reason) {
        return new TokenLog(tx, 1, block, FakeLogs.blockHashOf(block), TOKEN, TokenLog.Kind.MINTED, null, to,
                BigInteger.valueOf(amount), null, null, hex(reason));
    }

    static TokenLog burned(String tx, long block, String from, long amount, TokenReason reason) {
        return new TokenLog(tx, 1, block, FakeLogs.blockHashOf(block), TOKEN, TokenLog.Kind.BURNED, from, null,
                BigInteger.valueOf(amount), null, null, hex(reason));
    }

    static TokenLog subscribed(String tx, long block, String subscriber, String creator, long amount, long creatorShare, long platformShare) {
        return new TokenLog(tx, 0, block, FakeLogs.blockHashOf(block), TOKEN, TokenLog.Kind.SUBSCRIBED, subscriber, creator,
                BigInteger.valueOf(amount), BigInteger.valueOf(creatorShare), BigInteger.valueOf(platformShare), null);
    }

    static String hex(TokenReason reason) {
        return org.web3j.utils.Numeric.toHexString(reason.toBytes32().getValue());
    }
}
