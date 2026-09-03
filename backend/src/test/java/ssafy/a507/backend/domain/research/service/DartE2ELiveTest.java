package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.a507.backend.domain.research.entity.CorpFinancial;
import ssafy.a507.backend.domain.research.entity.CorpProfile;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.CorpFinancialRepository;
import ssafy.a507.backend.domain.research.repository.CorpProfileRepository;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/** 실제 DART → H2 적재까지 한 번에 돌린다. 운영 DB 는 건드리지 않는다. */
@SpringBootTest(properties = "app.dart.api-key=${DART_API_KEY:}")
@DisplayName("DART 수집 실호출 E2E")
class DartE2ELiveTest {

    @Autowired DartIngestService ingestService;
    @Autowired EntityManager em;
    @Autowired CorpProfileRepository corpProfileRepository;
    @Autowired CorpFinancialRepository corpFinancialRepository;
    @Autowired ResearchDocumentRepository researchDocumentRepository;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        assumeThat(System.getenv("DART_API_KEY")).as("DART_API_KEY 없음").isNotBlank();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            insertStock("005930", "삼성전자");
            insertStock("035720", "카카오");
            insertStock("340440", "세림B&G");
            em.flush();
        });
    }

    @Test
    @DisplayName("고유번호 → 개황 → 재무 → 공시 순으로 실제 적재된다")
    void 전체_파이프라인() {
        long t0 = System.currentTimeMillis();
        int seeded = ingestService.seedCorpCodes();
        System.out.println("[E2E] 고유번호 시드 " + seeded + "건 (" + (System.currentTimeMillis() - t0) + "ms)");
        assertThat(seeded).isEqualTo(3);

        t0 = System.currentTimeMillis();
        int profiles = ingestService.ingestProfiles();
        System.out.println("[E2E] 기업개황 " + profiles + "건 (" + (System.currentTimeMillis() - t0) + "ms)");

        t0 = System.currentTimeMillis();
        int financials = ingestService.ingestAnnualFinancials(ingestService.financialYear(LocalDate.now()));
        System.out.println("[E2E] 재무 " + financials + "행 (" + (System.currentTimeMillis() - t0) + "ms)");

        LocalDate today = LocalDate.now();
        t0 = System.currentTimeMillis();
        int docs = ingestService.ingestDisclosures(ingestService.disclosureFrom(today), today);
        System.out.println("[E2E] 공시 " + docs + "건 (" + (System.currentTimeMillis() - t0) + "ms)");

        for (CorpProfile p : corpProfileRepository.findAll()) {
            System.out.println("[E2E] 프로필 " + p.getStockCode() + " " + p.getCorpName()
                    + " corp=" + p.getCorpCode() + " 대표=" + p.getCeoName()
                    + " 업종=" + p.getIndustryCode() + " 설립=" + p.getEstablishedOn()
                    + " 결산월=" + p.getAccountMonth());
        }
        for (CorpFinancial f : corpFinancialRepository.findAll()) {
            System.out.println("[E2E] 재무 " + f.getStockCode() + " " + f.getFiscalYear()
                    + "Q" + f.getQuarter() + " " + f.getFsDiv() + " " + f.getCurrency()
                    + " 매출=" + f.getRevenue() + " 영업이익=" + f.getOperatingProfit()
                    + " 자본=" + f.getTotalEquity() + " rcept=" + f.getReceiptNo());
        }
        researchDocumentRepository.findAll().stream().limit(5).forEach(d ->
                System.out.println("[E2E] 공시 " + d.getStock().getCode() + " " + d.getExternalId()
                        + " " + d.getTitle() + " " + d.getPublishedAt()));

        // 재실행 멱등 — 두 번째 회차는 새로 쌓을 게 없어야 한다
        int again = ingestService.ingestDisclosures(ingestService.disclosureFrom(today), today);
        System.out.println("[E2E] 공시 재수집 " + again + "건 (0 이어야 한다)");
        assertThat(again).isZero();
        assertThat(corpProfileRepository.count()).isEqualTo(3);
    }

    void insertStock(String code, String name) {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, code)
                .setParameter(2, name)
                .executeUpdate();
    }
}
