package ssafy.a507.backend.domain.season.seed;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시더의 정리 규칙 — 스펙에 없는 옛 연습 시즌은 지우되 참가자가 있으면 남긴다.
 *
 * <p>시즌 생성 쪽은 여기서 검증하지 않는다. 테스트 DB 에는 일봉이 없어 생성이 전부
 * 건너뛰어지고, 생성 자체는 {@code SeasonCreateServiceTest} 가 본다.
 */
@SpringBootTest
@Transactional
class PracticeSeasonSeederTest {

    @Autowired PracticeSeasonSeeder seeder;
    @Autowired EntityManager em;

    @Test
    @DisplayName("스펙에 없는 연습 시즌은 종목·가격과 함께 지운다")
    void 스펙에_없는_시즌은_지운다() {
        Long stale = insertSeason("PRACTICE", "옛 업종", 1L);
        insertStock("A0001");
        Long ticker = insertTicker(stale, "A0001");
        insertPrice(ticker);
        em.flush();
        em.clear();

        seeder.run(null);

        assertThat(count("seasons")).isZero();
        assertThat(count("season_tickers")).isZero();
        assertThat(count("season_prices")).isZero();
    }

    @Test
    @DisplayName("참가자가 있는 옛 시즌은 남긴다 — 기록이 딸려 있다")
    void 참가자가_있으면_남긴다() {
        Long stale = insertSeason("PRACTICE", "옛 업종", 1L);
        Long user = insertUser("나");
        insertParticipant(stale, user);
        em.flush();
        em.clear();

        seeder.run(null);

        assertThat(count("seasons")).isEqualTo(1);
    }

    @Test
    @DisplayName("대회 시즌은 건드리지 않는다")
    void 대회는_건드리지_않는다() {
        insertSeason("COMPETITION", "화학", 1L);
        em.flush();
        em.clear();

        seeder.run(null);

        assertThat(count("seasons")).isEqualTo(1);
    }

    private Long insertSeason(String mode, String theme, long seed) {
        em.createNativeQuery(
                        """
                        INSERT INTO seasons (mode, title, theme, length_days, initial_cash, seed, current_day, status)
                        VALUES (?, '옛 시즌', ?, 5, 30000000, ?, 0, 'RUNNING')
                        """)
                .setParameter(1, mode)
                .setParameter(2, theme)
                .setParameter(3, seed)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT MAX(id) FROM seasons").getSingleResult()).longValue();
    }

    private void insertStock(String code) {
        em.createNativeQuery("INSERT INTO stocks (code, name, market, listed) VALUES (?, ?, 'KOSPI', true)")
                .setParameter(1, code)
                .setParameter(2, code + "종목")
                .executeUpdate();
    }

    private Long insertTicker(Long seasonId, String code) {
        em.createNativeQuery(
                        "INSERT INTO season_tickers (season_id, display_name, real_stock_code) VALUES (?, ?, ?)")
                .setParameter(1, seasonId)
                .setParameter(2, code + "종목")
                .setParameter(3, code)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT MAX(id) FROM season_tickers").getSingleResult()).longValue();
    }

    private void insertPrice(Long tickerId) {
        em.createNativeQuery("INSERT INTO season_prices (ticker_id, game_day, close) VALUES (?, 1, 1000)")
                .setParameter(1, tickerId)
                .executeUpdate();
    }

    private Long insertUser(String nickname) {
        em.createNativeQuery(
                        """
                        INSERT INTO users (nickname, role, status, created_at, updated_at)
                        VALUES (?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, nickname)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT id FROM users WHERE nickname = ?")
                        .setParameter(1, nickname)
                        .getSingleResult())
                .longValue();
    }

    private void insertParticipant(Long seasonId, Long userId) {
        em.createNativeQuery(
                        """
                        INSERT INTO season_participants (season_id, user_id, attempt_no, cash, current_day, created_at)
                        VALUES (?, ?, 1, ?, 0, CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, seasonId)
                .setParameter(2, userId)
                .setParameter(3, new BigDecimal("30000000"))
                .executeUpdate();
    }

    private long count(String table) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult()).longValue();
    }
}
