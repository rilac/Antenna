package ssafy.a507.backend.domain.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ssafy.a507.backend.domain.prediction.dto.PredictionDistributionResponse;
import ssafy.a507.backend.domain.prediction.dto.SettledTodayResponse;

/**
 * ANT-PRED-07 — 종목 예측 블록을 <b>실제 PostgreSQL</b> 로 본다. Docker 가 없으면 건너뛴다.
 *
 * <p>H2 로는 확인되지 않아 여기로 뺀 것 둘:
 *
 * <ol>
 *   <li><b>"오늘" 이 KST 자정에서 끊기는가.</b> {@code updated_at} 은 PG 에서 {@code timestamptz} 다. KST 00:00 은 UTC
 *       전날 15:00 이라, 경계를 UTC 로 자르거나 드라이버가 시간대를 한 번 더 옮기면 자정 직후 판정이 "어제" 로 빠진다.
 *   <li><b>numeric(14,2) 목표가와 원 단위 경계의 비교.</b> 229,075.00 이 229,075 경계의 위 구간에 드는가 — 자리수가 달라도
 *       같은 값으로 봐야 한다.
 * </ol>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("종목 예측 블록 — 실제 PostgreSQL")
class StockPredictionPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String STOCK = "005930";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired StockPredictionService service;
    @Autowired JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from prediction_commits");
        jdbc.update("delete from predictions");
        jdbc.update("delete from daily_quotes");
        jdbc.update("insert into stocks (code, name, listed) values (?, '삼성전자', true) on conflict do nothing", STOCK);
        userId = jdbc.queryForObject(
                "insert into users (wallet_address, role, status, created_at, updated_at)"
                        + " values (?, 'USER', 'ACTIVE', now(), now()) returning id",
                Long.class,
                "0x" + Long.toHexString(System.nanoTime()) + "0".repeat(8));
    }

    @Test
    @DisplayName("오늘 판정 — KST 자정 30초 뒤는 들고, 30초 전은 빠진다")
    void KST_자정_경계() {
        Instant todayStart = LocalDate.now(KST).atStartOfDay(KST).toInstant();
        long inside = insertPrediction("HIT", "82000", todayStart.plusSeconds(30));
        insertPrediction("MISS", "82000", todayStart.minusSeconds(30));

        assertThat(service.settledToday(STOCK).items())
                .extracting(SettledTodayResponse.Item::id)
                .containsExactly(inside);
    }

    @Test
    @DisplayName("분포 — numeric(14,2) 목표가 229,075.00 은 229,075 경계의 위 구간에 든다")
    void numeric_경계() {
        jdbc.update(
                "insert into daily_quotes (stock_code, trade_date, close, collected_at) values (?, ?, 269500, now())",
                STOCK,
                LocalDate.now(KST).minusDays(1));
        insertPrediction("OPEN", "229075.00", Instant.now());

        PredictionDistributionResponse res = service.distribution(STOCK);

        assertThat(res.total()).isEqualTo(1);
        assertThat(res.buckets().get(1).count()).isZero();
        assertThat(res.buckets().get(2).fromPrice()).isEqualByComparingTo("229075");
        assertThat(res.buckets().get(2).count()).isEqualTo(1);
    }

    private long insertPrediction(String status, String targetPrice, Instant updatedAt) {
        // OffsetDateTime 으로 넘긴다 — java.sql.Timestamp 는 JVM 기본 시간대를 거쳐 문자열이 되므로 경계 시험에 잡음이 낀다.
        return jdbc.queryForObject(
                "insert into predictions (user_id, track, stock_code, direction, target_price, horizon, status,"
                        + " error_rate, created_at, updated_at)"
                        + " values (?, 'REAL', ?, 'UP', ?::numeric, 7, ?, 1.5, now(), ?) returning id",
                Long.class,
                userId,
                STOCK,
                targetPrice,
                status,
                updatedAt.atOffset(ZoneOffset.UTC));
    }
}
