package ssafy.a507.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureDataSourceInitialization;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ANT-DB-02 — db/append-only.sql 의 트리거가 실제 PostgreSQL 에서 UPDATE/DELETE 를 막는지 본다.
 *
 * <p>트리거는 plpgsql 이라 H2 로 검증할 수 없다. PostgreSQL 은 실패한 문장이 트랜잭션을 통째로
 * abort 시키므로 테스트 트랜잭션을 끄고 문장마다 autocommit 으로 돈다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureDataSourceInitialization
@TestPropertySource(properties = "spring.sql.init.mode=always")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AppendOnlyTriggerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject(
                "insert into users (role, status, created_at, updated_at)"
                        + " values ('USER', 'ACTIVE', now(), now()) returning id",
                Long.class);
        jdbc.update("insert into stocks (code, name, listed) values ('005930', '삼성전자', true) on conflict do nothing");
    }

    @Test
    @DisplayName("token_ledger 는 UPDATE·DELETE 둘 다 막힌다")
    void 토큰_원장은_고칠_수_없다() {
        Long id = jdbc.queryForObject(
                "insert into token_ledger (user_id, delta, reason, created_at) values (?, 100, 'TEST', now()) returning id",
                Long.class, userId);
        assertAppendOnly("update token_ledger set delta = 0 where id = " + id);
        assertAppendOnly("delete from token_ledger where id = " + id);
    }

    @Test
    @DisplayName("chain_events 는 processed_at 만 바꿀 수 있고 삭제는 못 한다")
    void 체인_이벤트는_처리_시각만_바뀐다() {
        Long id = jdbc.queryForObject(
                "insert into chain_events (tx_hash, log_index, contract_address, event_name, block_number)"
                        + " values ('0xtx', 0, '0xcontract', 'Anchored', 1) returning id",
                Long.class);
        assertThatCode(() -> jdbc.update("update chain_events set processed_at = now() where id = ?", id))
                .doesNotThrowAnyException();
        assertAppendOnly("update chain_events set block_number = 2 where id = " + id);
        assertAppendOnly("delete from chain_events where id = " + id);
    }

    @Test
    @DisplayName("predictions 는 판정 컬럼만 바뀌고, 내용 변경과 삭제는 막힌다")
    void 예측은_판정_컬럼만_바뀐다() {
        Long id = jdbc.queryForObject(
                "insert into predictions"
                        + " (user_id, track, stock_code, direction, target_price, horizon, status, created_at, updated_at)"
                        + " values (?, 'REAL', '005930', 'UP', 70000, 7, 'BASE', now(), now()) returning id",
                Long.class, userId);
        assertThatCode(() -> jdbc.update(
                        "update predictions set status = 'OPEN', base_date = current_date, base_price = 69000,"
                                + " settle_date = current_date + 7, updated_at = now() where id = ?",
                        id))
                .doesNotThrowAnyException();
        assertAppendOnly("update predictions set target_price = 80000 where id = " + id);
        assertAppendOnly("update predictions set direction = 'DOWN' where id = " + id);
        assertAppendOnly("delete from predictions where id = " + id);
    }

    @Test
    @DisplayName("season_trades 에도 같은 트리거가 걸려 있다")
    void 체결_내역에도_트리거가_있다() {
        // 행을 만들려면 시즌·종목·참가자 FK 를 다 세워야 해서, token_ledger 와 같은 함수를 쓰는 트리거가 걸려 있는지만 본다.
        List<String> triggers = jdbc.queryForList(
                "select tgname from pg_trigger where tgrelid = 'season_trades'::regclass and not tgisinternal",
                String.class);
        assertThat(triggers).contains("trg_season_trades_append_only");
    }

    private void assertAppendOnly(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
