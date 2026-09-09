package ssafy.a507.backend.domain.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.common.security.SignatureGuard;
import ssafy.a507.backend.domain.prediction.dto.PredictionCreateRequest;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

/**
 * ANT-PRED-01 — 슬롯 3건이 <b>동시 요청</b>에서도 지켜지는지, 실제 PostgreSQL 로 본다 (plan §5-③).
 *
 * <p>H2 로는 검증이 안 되는 것: 사용자 행 {@code PESSIMISTIC_WRITE}({@code SELECT … FOR UPDATE})가 다른 커넥션의 같은 사용자
 * 요청을 실제로 줄 세우는가. 테스트 트랜잭션을 두지 않고 스레드마다 서비스 트랜잭션이 따로 커밋되게 한다.
 *
 * <p>서명은 mock 이다 — 실제 nonce 는 사용자당 하나만 살아 있어 동시 요청 다섯이 전부 서명을 통과할 수 없다. 여기서 보려는 건
 * 서명이 아니라 잠금이므로 {@link SignatureGuard} 만 대역으로 바꾼다. Docker 가 없으면 건너뛴다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("슬롯 잠금 — PostgreSQL 동시 요청")
class PredictionSlotLockPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String WALLET = "0x" + "11".repeat(20);
    private static final String NOTE_SALT = "0123456789abcdef".repeat(4);

    @Autowired PredictionRegisterService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SignatureGuard signatureGuard;

    private long userId;

    @BeforeEach
    void setUp() {
        given(signatureGuard.verify(anyLong(), any())).willReturn(WALLET);
        userId = jdbc.queryForObject(
                "insert into users (wallet_address, role, status, created_at, updated_at)"
                        + " values (?, 'USER', 'ACTIVE', now(), now()) returning id",
                Long.class, WALLET); // 컨테이너가 클래스마다 새로 뜨고 테스트가 하나라 유니크 충돌이 없다
        jdbc.update("insert into stocks (code, name, listed) values ('005930', '삼성전자', true) on conflict do nothing");
        jdbc.update(
                "insert into daily_quotes (stock_code, trade_date, close, collected_at) values ('005930', ?, 80000, now())"
                        + " on conflict do nothing",
                LocalDate.now().minusDays(1));
    }

    @Test
    @DisplayName("같은 사용자가 동시에 5건을 보내면 정확히 3건만 201 이고 2건은 PREDICTION_SLOT_EXCEEDED 다")
    void 동시_5건_중_3건만() throws Exception {
        int attempts = 5;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            BigDecimal price = new BigDecimal("81000").add(BigDecimal.valueOf(i));
            Callable<String> call = () -> {
                start.await();
                try {
                    service.register(userId, request(price));
                    return "OK";
                } catch (BusinessException e) {
                    return e.getErrorCode().name();
                }
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(3);
        assertThat(outcomes).filteredOn(ErrorCode.PREDICTION_SLOT_EXCEEDED.name()::equals).hasSize(2);
        Integer saved = jdbc.queryForObject("select count(*) from predictions where user_id = ?", Integer.class, userId);
        assertThat(saved).isEqualTo(3);
        Integer commits = jdbc.queryForObject(
                "select count(*) from prediction_commits c join predictions p on p.id = c.prediction_id where p.user_id = ?",
                Integer.class, userId);
        assertThat(commits).isEqualTo(3);
    }

    private static PredictionCreateRequest request(BigDecimal price) {
        return new PredictionCreateRequest(
                "005930", Prediction.Direction.UP, price, (short) 30, "근거", NOTE_SALT, List.of(), "0x" + "ab".repeat(65));
    }
}
