package ssafy.a507.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * {@code ddl-auto: update} 가 고치지 못하는 옛 스키마를 부팅 SQL 이 바로잡는지 본다.
 *
 * <p>2026-09-03 배포 장애 — users.nickname 이 08-31 에 NULL 허용으로 바뀌었지만(온보딩 전엔
 * 닉네임이 없다) update 는 NOT NULL 을 풀지 않는다. 그 전에 만들어진 배포 DB 는 NOT NULL 이
 * 남아 신규 가입 INSERT 가 전부 500 이었다. 로컬은 표를 새로 만들어 겪지 못했다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class SchemaRepairTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("옛 배포 DB 에 남은 users.nickname NOT NULL 을 부팅 SQL 이 푼다 — 두 번 돌려도 같다")
    void 옛_nickname_not_null_을_푼다() {
        // 08-31 이전에 만들어진 배포 DB 의 모습을 재현한다.
        jdbc.execute("ALTER TABLE users ALTER COLUMN nickname SET NOT NULL");
        assertThat(nickNullable()).isEqualTo("NO");

        runBootSql();
        assertThat(nickNullable()).isEqualTo("YES");

        runBootSql();
        assertThat(nickNullable()).as("매 부팅마다 도는 파일이라 멱등이어야 한다").isEqualTo("YES");
    }

    private String nickNullable() {
        return jdbc.queryForObject(
                """
                select is_nullable from information_schema.columns
                where table_name = 'users' and column_name = 'nickname'
                """,
                String.class);
    }

    /** application.yaml 의 spring.sql.init 과 같은 방식(파일 전체 한 문장)으로 돌린다. */
    private void runBootSql() {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("db/append-only.sql"));
        populator.setSeparator("^^^ END OF SCRIPT ^^^");
        populator.execute(dataSource);
    }
}
