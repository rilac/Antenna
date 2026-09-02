package ssafy.a507.backend.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import ssafy.a507.backend.domain.account.entity.User;

/**
 * ANT-DB-01 의 CHECK 제약 — 앱 검증을 우회한 INSERT 도 DB 가 막는지 본다.
 *
 * <p>엔티티에 생성자·팩터리가 없어 JDBC 로 직접 넣는다. 정상 행이 들어가는 것도 함께 확인해
 * 실패 원인이 FK 나 NOT NULL 이 아니라 CHECK 임을 분리한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaCheckConstraintTest {

    private static final String SAMSUNG = "005930";

    @Autowired JdbcTemplate jdbcTemplate;
    @PersistenceContext EntityManager entityManager;

    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        User user = User.create();
        User other = User.create();
        entityManager.persist(user);
        entityManager.persist(other);
        entityManager.flush();
        userId = user.getId();
        otherUserId = other.getId();
        jdbcTemplate.update("insert into stocks (code, name, listed) values (?, '삼성전자', true)", SAMSUNG);
    }

    @Test
    @DisplayName("예측 대상은 트랙에 맞는 것 하나만 — REAL 은 종목, REPLAY 는 시즌 종목")
    void 예측_대상은_트랙과_맞아야_한다() {
        assertThatCode(() -> insertPrediction("REAL", SAMSUNG, 7)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertPrediction("REAL", null, 7))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPrediction("REPLAY", SAMSUNG, 7))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("REAL horizon 은 7/14/30/90 만 허용한다")
    void REAL_horizon_은_고정_집합이다() {
        assertThatThrownBy(() -> insertPrediction("REAL", SAMSUNG, 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("자기 구독은 DB 가 막는다")
    void 자기_구독은_거부된다() {
        assertThatCode(() -> insertSubscription(userId, otherUserId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertSubscription(userId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertPrediction(String track, String stockCode, int horizon) {
        jdbcTemplate.update(
                "insert into predictions"
                        + " (user_id, track, stock_code, direction, target_price, horizon, status, created_at, updated_at)"
                        + " values (?, ?, ?, 'UP', 70000, ?, 'BASE', current_timestamp, current_timestamp)",
                userId, track, stockCode, horizon);
    }

    private void insertSubscription(Long subscriberId, Long publisherId) {
        jdbcTemplate.update(
                "insert into subscriptions"
                        + " (subscriber_id, publisher_id, fee, status, auto_renew, created_at, updated_at)"
                        + " values (?, ?, 0, 'PENDING', false, current_timestamp, current_timestamp)",
                subscriberId, publisherId);
    }
}
