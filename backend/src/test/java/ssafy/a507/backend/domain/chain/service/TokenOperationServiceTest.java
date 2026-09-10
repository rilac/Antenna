package ssafy.a507.backend.domain.chain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.chain.entity.Operation;
import ssafy.a507.backend.domain.chain.relay.TokenReason;
import ssafy.a507.backend.domain.chain.repository.OperationRepository;

/**
 * ANT-CHAIN-10 — 202 작업의 tx 전송과 그 결과 기록. 체인은 {@link FakeTokenRelayer} 가 대신한다.
 *
 * <p><b>팀 컨벤션인 {@code @Transactional} 롤백을 쓰지 않는다.</b> 검증 대상인 {@code dispatch*} 가 "활성 트랜잭션 안에서는
 * 거부" 를 계약으로 갖기 때문이다 — 테스트 트랜잭션 안에서 부르면 그 계약이 먼저 터진다. 대신 픽스처는
 * {@link TransactionTemplate} 으로 커밋하고 {@code @AfterEach} 가 만든 행을 지운다. H2 다.
 */
@SpringBootTest
@Import(FakeTokenRelayer.Config.class)
@DisplayName("토큰 작업 전송(TokenOperationService)")
class TokenOperationServiceTest {

    private static final String WALLET_A = "0x00000000000000000000000000000000000000aa";
    private static final String WALLET_B = "0x00000000000000000000000000000000000000bb";
    private static final BigInteger AMOUNT = BigInteger.valueOf(2000);

    @Autowired TokenOperationService service;
    @Autowired FakeTokenRelayer relayer;
    @Autowired OperationRepository operations;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManager em;

    private final List<Long> userIds = new ArrayList<>();
    private final List<String> operationIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        relayer.reset();
    }

    @AfterEach
    void tearDown() {
        tx.execute(s -> {
            for (String id : operationIds) {
                operations.deleteById(id);
            }
            // remove 는 flush 때 나가고 아래 JPQL delete 는 즉시 나간다 — 순서를 맞추지 않으면 FK 가 사용자 삭제를 막는다.
            em.flush();
            for (Long id : userIds) {
                em.createQuery("delete from User u where u.id = :id").setParameter("id", id).executeUpdate();
            }
            return null;
        });
        operationIds.clear();
        userIds.clear();
    }

    // ── 정상 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("소각 전송 성공 — Operation 에 tx_hash 가 채워지고 상태는 PENDING 그대로(확정은 인덱서)")
    void 소각_전송_성공() {
        String opId = accept(newUser(WALLET_A), Operation.Kind.PREDICTION_BURN);
        relayer.thenSent();

        String txHash = service.dispatchBurn(opId, AMOUNT, TokenReason.SLOT_OVER);

        Operation op = operations.findById(opId).orElseThrow();
        assertThat(op.getTxHash()).isEqualTo(txHash).startsWith("0x");
        assertThat(op.getStatus()).isEqualTo(Operation.Status.PENDING);
        assertThat(op.getSettledAt()).isNull();
        assertThat(relayer.calls()).singleElement().satisfies(c -> {
            assertThat(c.kind()).isEqualTo("burn");
            assertThat(c.from()).isEqualTo(WALLET_A);
            assertThat(c.amount()).isEqualTo(AMOUNT);
            assertThat(c.reason()).isEqualTo(TokenReason.SLOT_OVER);
        });
    }

    @Test
    @DisplayName("mint 는 Operation 사용자의 지갑으로 간다")
    void 발행_전송() {
        String opId = accept(newUser(WALLET_A), Operation.Kind.SEASON_JOIN);

        service.dispatchMint(opId, BigInteger.valueOf(1000), TokenReason.SEASON_REWARD);

        assertThat(relayer.calls()).singleElement().satisfies(c -> {
            assertThat(c.kind()).isEqualTo("mint");
            assertThat(c.to()).isEqualTo(WALLET_A);
        });
    }

    @Test
    @DisplayName("구독은 Operation 사용자(구독자) → creatorUserId(예측가) 지갑으로 간다")
    void 구독_전송() {
        Long creator = newUser(WALLET_B);
        String opId = accept(newUser(WALLET_A), Operation.Kind.SUBSCRIBE);

        service.dispatchSubscribe(opId, creator, BigInteger.valueOf(30_000));

        assertThat(relayer.calls()).singleElement().satisfies(c -> {
            assertThat(c.kind()).isEqualTo("subscribe");
            assertThat(c.from()).isEqualTo(WALLET_A);
            assertThat(c.to()).isEqualTo(WALLET_B);
            assertThat(c.amount()).isEqualTo(BigInteger.valueOf(30_000));
        });
        assertThat(operations.findById(opId).orElseThrow().getTxHash()).isNotNull();
    }

    // ── 실패는 FAILED 로 닫고 그 상태코드로 ─────────────────────────────

    @Test
    @DisplayName("시뮬레이션 잔액 부족 → Operation FAILED(INSUFFICIENT_BALANCE), 예외는 409 그대로")
    void 잔액_부족() {
        String opId = accept(newUser(WALLET_A), Operation.Kind.PREDICTION_BURN);
        relayer.thenInsufficientBalance();

        assertThatThrownBy(() -> service.dispatchBurn(opId, AMOUNT, TokenReason.SLOT_OVER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        Operation op = operations.findById(opId).orElseThrow();
        assertThat(op.getStatus()).isEqualTo(Operation.Status.FAILED);
        assertThat(op.getErrorCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(op.getTxHash()).isNull();
        assertThat(op.getSettledAt()).isNotNull();
    }

    @Test
    @DisplayName("RPC 장애 → FAILED(CHAIN_UNAVAILABLE) + 503. 즉시 재시도는 없다 — 릴레이어 호출 1회")
    void RPC_장애() {
        String opId = accept(newUser(WALLET_A), Operation.Kind.PREDICTION_BURN);
        relayer.thenUnavailable();

        assertThatThrownBy(() -> service.dispatchBurn(opId, AMOUNT, TokenReason.SLOT_OVER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAIN_UNAVAILABLE);

        assertThat(operations.findById(opId).orElseThrow().getErrorCode()).isEqualTo("CHAIN_UNAVAILABLE");
        assertThat(relayer.calls()).hasSize(1);
    }

    @Test
    @DisplayName("컨트랙트 거부(서버 버그) → FAILED(에러 이름) + 500 INTERNAL_ERROR")
    void 컨트랙트_거부() {
        Long creator = newUser(WALLET_B);
        String opId = accept(newUser(WALLET_A), Operation.Kind.SUBSCRIBE);
        relayer.thenRevert("SelfSubscribe");

        assertThatThrownBy(() -> service.dispatchSubscribe(opId, creator, AMOUNT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);

        assertThat(operations.findById(opId).orElseThrow().getErrorCode()).isEqualTo("SelfSubscribe");
    }

    @Test
    @DisplayName("릴레이어가 꺼져 있으면 requireEnabled 가 503 — 접수 전에 부르므로 아무것도 안 쓴다")
    void 릴레이어_꺼짐() {
        relayer.disable();

        assertThatThrownBy(() -> service.requireEnabled())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAIN_UNAVAILABLE);
    }

    // ── 문 앞에서 막는 것 ───────────────────────────────────────────────

    @Test
    @DisplayName("지갑 미연동 사용자의 작업은 WALLET_NOT_LINKED 로 FAILED — 릴레이어는 호출되지 않는다")
    void 지갑_미연동() {
        String opId = accept(newUser(null), Operation.Kind.PREDICTION_BURN);

        assertThatThrownBy(() -> service.dispatchBurn(opId, AMOUNT, TokenReason.SLOT_OVER))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_NOT_LINKED);

        assertThat(relayer.calls()).isEmpty();
        assertThat(operations.findById(opId).orElseThrow().getStatus()).isEqualTo(Operation.Status.FAILED);
    }

    @Test
    @DisplayName("이미 tx 를 보낸 작업을 다시 dispatch 하면 IllegalStateException — 이중 소각 방지")
    void 이중_전송_거부() {
        String opId = accept(newUser(WALLET_A), Operation.Kind.PREDICTION_BURN);
        service.dispatchBurn(opId, AMOUNT, TokenReason.SLOT_OVER);

        assertThatThrownBy(() -> service.dispatchBurn(opId, AMOUNT, TokenReason.SLOT_OVER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(relayer.calls()).hasSize(1);
    }

    @Test
    @DisplayName("활성 트랜잭션 안에서 dispatch 하면 IllegalStateException — 접수를 먼저 커밋해야 한다")
    void 트랜잭션_안에서_거부() {
        String opId = accept(newUser(WALLET_A), Operation.Kind.PREDICTION_BURN);

        assertThatThrownBy(() -> tx.execute(s -> service.dispatchBurn(opId, AMOUNT, TokenReason.SLOT_OVER)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(relayer.calls()).isEmpty();
        assertThat(operations.findById(opId).orElseThrow().getTxHash()).isNull();
    }

    // ── 픽스처 ────────────────────────────────────────────────────────

    private Long newUser(String wallet) {
        Long id = tx.execute(s -> {
            User user = User.create();
            if (wallet != null) {
                user.linkWallet(wallet);
            }
            em.persist(user);
            return user.getId();
        });
        userIds.add(id);
        return id;
    }

    private String accept(Long userId, Operation.Kind kind) {
        String id = tx.execute(s -> {
            User user = em.find(User.class, userId);
            Operation op = Operation.accept(user, kind, null, null);
            em.persist(op);
            return op.getId();
        });
        operationIds.add(id);
        return id;
    }
}
