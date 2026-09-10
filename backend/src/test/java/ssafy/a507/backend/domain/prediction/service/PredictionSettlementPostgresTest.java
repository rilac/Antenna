package ssafy.a507.backend.domain.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ANT-PRED-03·04 — 판정 배치를 <b>실제 PostgreSQL</b> 로 본다. Docker 가 없으면 건너뛴다.
 *
 * <p>H2 로는 확인되지 않아 여기로 뺀 것 셋:
 *
 * <ol>
 *   <li><b>{@code settled_on} 컬럼이 실제로 붙는가.</b> 이 프로젝트는 {@code ddl-auto: update} 라 엔티티를 고치면 팀 DB 스키마에
 *       번진다. 그리고 <b>행이 있는 표에 NOT NULL 컬럼을 더하면 Hibernate 가 조용히 실패한 전례</b>가 있다
 *       ({@code PredictionCommit.noteHash} 주석). nullable 로 뒀으니 안전해야 하는데, 안전하다는 걸 확인한다.
 *   <li><b>{@code batch_runs.business_date} UNIQUE 가 재실행에서 안 터지는가.</b> H2 와 제약 동작이 다르다.
 *   <li><b>numeric(6,3)·numeric(14,2) 에 값이 그대로 들어가는가.</b> 반올림 자리수가 드라이버마다 다를 수 있다.
 * </ol>
 *
 * <p>앱을 통째로 띄우는 대신 이 경로를 고른 이유: {@code bootRun} 으로 확인하면 앵커 배치가 같이 켜져
 * <b>팀 공용 컨트랙트의 batchId 가 소모된다</b>. 여기서는 컨테이너가 클래스마다 새로 뜨고 체인은 건드리지 않는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("판정 배치 — 실제 PostgreSQL")
class PredictionSettlementPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String STOCK = "005930";
    private static final LocalDate MON = LocalDate.of(2026, 1, 5);
    private static final LocalDate SUN = LocalDate.of(2026, 1, 11);
    private static final LocalDate NEXT_MON = LocalDate.of(2026, 1, 12);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 1, 13);

    @Autowired PredictionSettlementRunner runner;
    @Autowired JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from prediction_commits");
        jdbc.update("delete from predictions");
        jdbc.update("delete from batch_runs");
        jdbc.update("insert into stocks (code, name, listed) values (?, '삼성전자', true) on conflict do nothing", STOCK);
        userId = jdbc.queryForObject(
                "insert into users (wallet_address, role, status, created_at, updated_at)"
                        + " values (?, 'USER', 'ACTIVE', now(), now()) returning id",
                Long.class,
                "0x" + Long.toHexString(System.nanoTime()) + "0".repeat(8));
    }

    @Test
    @Order(1)
    @DisplayName("settled_on 이 date · nullable 로 실제 생성된다 — 엔티티에 쓴 그대로")
    void 컬럼_생성() {
        Map<String, Object> col = jdbc.queryForMap(
                "select data_type, is_nullable from information_schema.columns"
                        + " where table_name = 'predictions' and column_name = 'settled_on'");

        assertThat(col.get("data_type")).isEqualTo("date");
        assertThat(col.get("is_nullable")).isEqualTo("YES");
    }

    @Test
    @Order(2)
    @DisplayName("행이 이미 있는 predictions 에 settled_on 을 더해도 기존 행이 안 깨진다 — ddl-auto 로 팀 DB 에 번지는 상황 재현")
    void 행이_있는_표에_컬럼_추가() {
        // 실제 배포 순서를 그대로 만든다: 컬럼이 없던 시절의 표 → 행이 쌓임 → 우리 변경이 컬럼을 더함
        jdbc.update("alter table predictions drop column settled_on");
        long id = insertPrediction("UP", "82000", MON, SUN, "BASE", null);

        assertThatCode(() -> jdbc.update("alter table predictions add column settled_on date"))
                .doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("select settled_on from predictions where id = ?", LocalDate.class, id))
                .isNull();
        assertThat(jdbc.queryForObject("select count(*) from predictions where id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    @Order(3)
    @DisplayName("같은 날 두 번 돌려도 batch_runs 는 한 행이고 카운터가 누적된다 — business_date UNIQUE 를 실제 제약으로 확인")
    void 재실행_UNIQUE() {
        insertQuote(MON, "80000");
        insertPrediction("UP", "82000", MON, LocalDate.of(2030, 1, 7), "BASE", null);
        runner.run(BUSINESS_DATE);

        // 회차 사이에 새 예측이 하나 들어왔다
        insertPrediction("UP", "83000", MON, LocalDate.of(2030, 1, 7), "BASE", null);
        assertThatCode(() -> runner.run(BUSINESS_DATE)).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("select count(*) from batch_runs", Integer.class)).isEqualTo(1);
        Map<String, Object> run = jdbc.queryForMap(
                "select opened_count, verified_count, status from batch_runs where business_date = ?", BUSINESS_DATE);
        assertThat(run.get("opened_count")).isEqualTo(2);
        assertThat(run.get("verified_count")).isEqualTo(0);
        assertThat(run.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    @Order(4)
    @DisplayName("판정 한 바퀴 — 만기일이 일요일이면 월요일 종가를 쓰고 settled_on 에 그 날짜가 남는다")
    void 판정_한_바퀴() {
        insertQuote(MON, "80000");
        insertQuote(NEXT_MON, "85000"); // 일요일에는 시세가 없다
        long id = insertPrediction("UP", "82000", MON, SUN, "BASE", null);

        runner.run(BUSINESS_DATE);

        Map<String, Object> p = jdbc.queryForMap(
                "select status, base_price, settle_price, settled_on, settle_date, error_rate from predictions where id = ?",
                id);
        assertThat(p.get("status")).isEqualTo("HIT");
        assertThat((BigDecimal) p.get("base_price")).isEqualByComparingTo("80000");
        assertThat((BigDecimal) p.get("settle_price")).isEqualByComparingTo("85000");
        // 검산 sourceUrl 이 이 날짜로 열린다. settle_date(일요일)로 열면 포털이 빈 응답을 준다
        assertThat(((java.sql.Date) p.get("settled_on")).toLocalDate()).isEqualTo(NEXT_MON);
        assertThat(((java.sql.Date) p.get("settle_date")).toLocalDate()).isEqualTo(SUN);

        // numeric(6,3) 에 소수 셋째 자리까지 그대로 들어갔는가
        BigDecimal error = (BigDecimal) p.get("error_rate");
        assertThat(error).isEqualByComparingTo("3.659");
        assertThat(error.scale()).isEqualTo(3);

        Map<String, Object> run = jdbc.queryForMap(
                "select opened_count, verified_count, hit_count from batch_runs where business_date = ?", BUSINESS_DATE);
        assertThat(run.get("opened_count")).isEqualTo(1);
        assertThat(run.get("verified_count")).isEqualTo(1);
        assertThat(run.get("hit_count")).isEqualTo(1);
    }

    // ── 픽스처 ──────────────────────────────────────────────────────

    private long insertPrediction(
            String direction, String targetPrice, LocalDate baseDate, LocalDate settleDate, String status, String basePrice) {
        return jdbc.queryForObject(
                """
                insert into predictions
                  (user_id, track, stock_code, direction, target_price, ref_close, horizon,
                   base_date, settle_date, base_price, status, created_at, updated_at)
                values (?, 'REAL', ?, ?, ?, 80000, 30, ?, ?, ?, ?, now(), now())
                returning id
                """,
                Long.class,
                userId,
                STOCK,
                direction,
                new BigDecimal(targetPrice),
                baseDate,
                settleDate,
                basePrice == null ? null : new BigDecimal(basePrice),
                status);
    }

    private void insertQuote(LocalDate tradeDate, String close) {
        jdbc.update(
                "insert into daily_quotes (stock_code, trade_date, close, collected_at)"
                        + " values (?, ?, ?, now()) on conflict do nothing",
                STOCK,
                tradeDate,
                new BigDecimal(close));
    }
}
