package ssafy.a507.backend.domain.research.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.research.entity.AiBriefing;

/**
 * 브리핑 리포지토리를 실제 PostgreSQL 에서 본다 (ANT-RESEARCH-03).
 *
 * <p>H2 로는 못 보는 것 둘 — {@code (:stockCode is null and b.stock is null or …)} 의 문자열
 * null 바인딩이 Postgres 드라이버에서도 타입 오류 없이 통하는지, 그리고 UQ 가 MARKET(stock_code
 * NULL) 겹침을 잡지 못한다는 사실. 후자는 "그래서 서비스가 조회 후 rewrite 한다"는 설계의 근거다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("AI 브리핑 리포지토리 (PostgreSQL)")
class AiBriefingRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String SAMSUNG = "005930";
    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);

    @Autowired AiBriefingRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        jdbc.update("insert into stocks (code, name, listed) values (?, '삼성전자', true) on conflict do nothing", SAMSUNG);
        Stock stock = em.getReference(Stock.class, SAMSUNG);
        repository.save(AiBriefing.of(AiBriefing.Scope.MARKET, null, D1, "시장 1일", "본문", "v1"));
        repository.save(AiBriefing.of(AiBriefing.Scope.STOCK, stock, D1, "삼성 1일", "본문", "v1"));
        repository.save(AiBriefing.of(AiBriefing.Scope.MARKET, null, D2, "시장 2일", "본문", "v1"));
        repository.save(AiBriefing.of(AiBriefing.Scope.STOCK, stock, D2, "삼성 2일", "본문", "v1"));
        em.flush();
    }

    @Test
    @DisplayName("stockCode null 바인딩 — MARKET 은 stock 이 NULL 인 행을, STOCK 은 코드가 같은 행을 찾는다")
    void findTarget_null_바인딩() {
        assertThat(repository.findTarget(AiBriefing.Scope.MARKET, null, D2))
                .singleElement().extracting(AiBriefing::getHeadline).isEqualTo("시장 2일");
        assertThat(repository.findTarget(AiBriefing.Scope.STOCK, SAMSUNG, D1))
                .singleElement().extracting(AiBriefing::getHeadline).isEqualTo("삼성 1일");
        assertThat(repository.findTarget(AiBriefing.Scope.STOCK, "000000", D1)).isEmpty();
    }

    @Test
    @DisplayName("목록·최신 날짜 — 필터 셋이 전부 null 이어도 MARKET 행이 빠지지 않는다")
    void findAllOn_findLatest() {
        assertThat(repository.findLatestTargetDate(null, null)).contains(D2);
        assertThat(repository.findLatestTargetDate(AiBriefing.Scope.STOCK, SAMSUNG)).contains(D2);
        assertThat(repository.findAllOn(null, null, D2)).extracting(AiBriefing::getHeadline)
                .containsExactly("시장 2일", "삼성 2일");
        assertThat(repository.findAllOn(AiBriefing.Scope.MARKET, null, D1)).extracting(AiBriefing::getHeadline)
                .containsExactly("시장 1일");
        assertThat(repository.findAllOn(null, SAMSUNG, D1)).extracting(AiBriefing::getHeadline)
                .containsExactly("삼성 1일");
    }

    @Test
    @DisplayName("UQ — 같은 종목·같은 날짜 STOCK 은 두 번 못 넣는다")
    void uq_stock() {
        Stock stock = em.getReference(Stock.class, SAMSUNG);
        assertThatThrownBy(() -> {
            repository.save(AiBriefing.of(AiBriefing.Scope.STOCK, stock, D2, "중복", "본문", "v1"));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("UQ — MARKET(stock_code NULL) 겹침은 Postgres 유니크가 못 잡는다. 서비스가 findTarget 뒤 rewrite 하는 이유")
    void uq_market_은_못_잡는다() {
        repository.save(AiBriefing.of(AiBriefing.Scope.MARKET, null, D2, "중복 시장", "본문", "v1"));
        em.flush();
        assertThat(jdbc.queryForObject(
                        "select count(*) from ai_briefings where scope = 'MARKET' and target_date = ?", Long.class, D2))
                .isEqualTo(2L);
        // 둘이 있어도 조회는 죽지 않고 먼저 만든 행이 앞이다 — 서비스가 첫 행을 집어 rewrite 한다.
        assertThat(repository.findTarget(AiBriefing.Scope.MARKET, null, D2))
                .extracting(AiBriefing::getHeadline).containsExactly("시장 2일", "중복 시장");
    }
}
