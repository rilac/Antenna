package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.a507.backend.domain.research.entity.AiBriefing;
import ssafy.a507.backend.domain.research.repository.AiBriefingRepository;

/**
 * GMS 를 실제로 두 번 부른다(시장 1 + 종목 1). 운영 DB 는 건드리지 않는다 — 재료는 H2 에 합성으로 넣는다.
 *
 * <p>키가 없으면 조용히 건너뛴다. 한도를 나눠 쓰는 키라 자동으로 돌리지 않고 사람이 시킬 때만
 * {@code --tests '*E2ELiveTest*' --rerun} 으로 돈다.
 *
 * <p>Gradle 은 표준출력을 콘솔에 흘리지 않는다. 결과는
 * {@code build/test-results/test/TEST-*.xml} 에서 {@code [E2E]} 로 찾는다.
 */
@SpringBootTest(properties = "app.ai.api-key=${AI_API_KEY:}")
@DisplayName("AI 브리핑 실호출 E2E")
class BriefingE2ELiveTest {

    private static final String SAMSUNG = "005930";
    private static final LocalDate TARGET = LocalDate.of(2026, 9, 2);

    @Autowired BriefingGenerationService generationService;
    @Autowired BriefingService briefingService;
    @Autowired AiBriefingRepository aiBriefingRepository;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        assumeThat(System.getenv("AI_API_KEY")).as("AI_API_KEY 없음").isNotBlank();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, "삼성전자")
                    .executeUpdate();
            for (int i = 0; i < 25; i++) {
                em.createNativeQuery(
                                "INSERT INTO daily_quotes (stock_code, trade_date, close, volume, collected_at)"
                                        + " VALUES (?, ?, ?, ?, ?)")
                        .setParameter(1, SAMSUNG)
                        .setParameter(2, TARGET.minusDays(24 - i))
                        .setParameter(3, new BigDecimal(70000 + i * 150))
                        .setParameter(4, 10_000_000L + i * 200_000L)
                        .setParameter(5, Instant.now())
                        .executeUpdate();
                if (i >= 19) {
                    em.createNativeQuery(
                                    "INSERT INTO index_quotes (index_code, trade_date, close) VALUES ('KOSPI', ?, ?)")
                            .setParameter(1, TARGET.minusDays(24 - i))
                            .setParameter(2, new BigDecimal(3150 + i * 10))
                            .executeUpdate();
                }
            }
            em.createNativeQuery(
                            "INSERT INTO corp_financials (stock_code, fiscal_year, quarter, fs_div, revenue,"
                                    + " operating_profit, net_income, total_liabilities, total_equity, updated_at)"
                                    + " VALUES (?, 2025, 4, 'CFS', ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, 300_000_000_000_000L)
                    .setParameter(3, 32_000_000_000_000L)
                    .setParameter(4, 34_000_000_000_000L)
                    .setParameter(5, 112_000_000_000_000L)
                    .setParameter(6, 400_000_000_000_000L)
                    .setParameter(7, Instant.now())
                    .executeUpdate();
            em.flush();
        });
    }

    @Test
    @DisplayName("시장 + 종목 브리핑이 실제로 생성되고 조회된다")
    void 생성_조회() {
        long t0 = System.currentTimeMillis();
        int written = generationService.generate();
        System.out.println("[E2E] 브리핑 " + written + "건 (" + (System.currentTimeMillis() - t0) + "ms)");
        assertThat(written).as("D16 가드에 둘 다 걸리면 0 — 응답 로그를 본다").isPositive();

        for (AiBriefing b : aiBriefingRepository.findAll()) {
            System.out.println("[E2E] " + b.getScope() + " | " + b.getHeadline());
            System.out.println("[E2E]   " + b.getBody().replace("\n", " / "));
            assertThat(b.getHeadline()).isNotBlank();
            assertThat(b.getBody().length()).isLessThanOrEqualTo(AiBriefing.MAX_BODY_LENGTH);
            assertThat(BriefingGenerationService.FORBIDDEN.matcher(b.getBody()).find()).isFalse();
        }

        var items = briefingService.list(null, null, null).items();
        assertThat(items).hasSize(written);
        assertThat(briefingService.detail(items.get(0).id()).body()).isNotBlank();
    }
}
