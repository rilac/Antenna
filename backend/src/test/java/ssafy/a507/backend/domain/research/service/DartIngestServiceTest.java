package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.research.client.CorpCodeRow;
import ssafy.a507.backend.domain.research.client.DartClient;
import ssafy.a507.backend.domain.research.client.DartCompany;
import ssafy.a507.backend.domain.research.client.DartDisclosure;
import ssafy.a507.backend.domain.research.client.DartException;
import ssafy.a507.backend.domain.research.client.DartFinancialSnapshot;
import ssafy.a507.backend.domain.research.entity.CorpFinancial;
import ssafy.a507.backend.domain.research.entity.CorpProfile;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.CorpFinancialRepository;
import ssafy.a507.backend.domain.research.repository.CorpProfileRepository;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * 수집의 재실행 안전성을 본다.
 *
 * <p>배치는 실패한 회차를 다음 회차가 덮는 구조라 <b>같은 것을 여러 번 받는 게 정상</b>이다.
 * 그때 중복 행이 생기거나 유니크 위반으로 배치가 통째로 깨지면 수집이 매일 멈춘다.
 *
 * <p>DART 는 스텁이다 — 테스트는 외부와 통신하지 않는다. 키는 여기서 주입한다. 기본
 * 테스트 설정은 키를 비워 두는데, 그 상태를 검증하는 것은 {@link DartIngestNoKeyTest} 다.
 */
@SpringBootTest(properties = "app.dart.api-key=test-key")
@Transactional
@DisplayName("DART 수집")
class DartIngestServiceTest {

    private static final String SAMSUNG = "005930";
    private static final String KAKAO = "035720";
    private static final String SAMSUNG_CORP = "00126380";

    @Autowired DartIngestService ingestService;
    @Autowired EntityManager em;
    @Autowired CorpProfileRepository corpProfileRepository;
    @Autowired CorpFinancialRepository corpFinancialRepository;
    @Autowired ResearchDocumentRepository researchDocumentRepository;

    @MockitoBean DartClient dartClient;

    @BeforeEach
    void setUp() {
        insertStock(SAMSUNG, "삼성전자");
        insertStock(KAKAO, "카카오");
        em.flush();
    }

    @Test
    @DisplayName("고유번호 시드는 stocks 에 있는 종목만 남긴다")
    void 시드는_수집_범위만_남긴다() {
        given(dartClient.fetchListedCorpCodes())
                .willReturn(List.of(
                        new CorpCodeRow(SAMSUNG_CORP, "삼성전자", SAMSUNG),
                        new CorpCodeRow("00258801", "카카오", KAKAO),
                        // stocks 에 없는 종목. DART 에는 4천 곳이 있고 우리는 300 곳만 본다.
                        new CorpCodeRow("00999999", "범위밖", "999999")));

        int seeded = ingestService.seedCorpCodes();
        em.flush();

        assertThat(seeded).isEqualTo(2);
        assertThat(corpProfileRepository.findAll())
                .extracting(CorpProfile::getStockCode)
                .containsExactlyInAnyOrder(SAMSUNG, KAKAO);
    }

    @Test
    @DisplayName("같은 시드를 다시 돌려도 행이 늘지 않는다")
    void 시드_재실행_멱등() {
        given(dartClient.fetchListedCorpCodes())
                .willReturn(List.of(new CorpCodeRow(SAMSUNG_CORP, "삼성전자", SAMSUNG)));

        ingestService.seedCorpCodes();
        em.flush();
        int second = ingestService.seedCorpCodes();
        em.flush();

        assertThat(second).as("바뀐 게 없으면 아무것도 쓰지 않는다").isZero();
        assertThat(corpProfileRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("같은 공시를 다시 받아도 한 건이다 — 접수번호가 멱등 키다")
    void 공시_재수집_멱등() {
        seedSamsung();
        given(dartClient.fetchDisclosures(anyString(), any(), any()))
                .willReturn(List.of(disclosure("20260831000066", "임원ㆍ주요주주특정증권등소유상황보고서")));

        LocalDate today = LocalDate.of(2026, 9, 2);
        int first = ingestService.ingestDisclosures(today.minusDays(7), today);
        em.flush();
        int second = ingestService.ingestDisclosures(today.minusDays(7), today);
        em.flush();

        assertThat(first).isEqualTo(1);
        assertThat(second).as("두 번째 회차는 새로 쌓을 게 없다").isZero();
        assertThat(researchDocumentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("한 응답 안에 같은 접수번호가 두 번 와도 깨지지 않는다")
    void 응답_내_중복도_거른다() {
        seedSamsung();
        given(dartClient.fetchDisclosures(anyString(), any(), any()))
                .willReturn(List.of(
                        disclosure("20260831000066", "보고서"),
                        disclosure("20260831000066", "보고서")));

        LocalDate today = LocalDate.of(2026, 9, 2);
        int created = ingestService.ingestDisclosures(today.minusDays(7), today);
        em.flush();

        assertThat(created).isEqualTo(1);
    }

    @Test
    @DisplayName("공시는 본문 없이 제목과 원문 링크만 저장한다")
    void 공시는_링크만_저장한다() {
        seedSamsung();
        given(dartClient.fetchDisclosures(anyString(), any(), any()))
                .willReturn(List.of(disclosure("20260831000066", "사업보고서")));

        LocalDate today = LocalDate.of(2026, 9, 2);
        ingestService.ingestDisclosures(today.minusDays(7), today);
        em.flush();

        ResearchDocument saved = researchDocumentRepository.findAll().get(0);
        assertThat(saved.getSource()).isEqualTo(ResearchDocument.Source.DART);
        assertThat(saved.getStock().getCode()).isEqualTo(SAMSUNG);
        assertThat(saved.getTitle()).isEqualTo("사업보고서");
        assertThat(saved.getOriginUrl())
                .isEqualTo("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260831000066");
        assertThat(saved.getSummary()).as("요약은 배치 B6 의 몫이다").isNull();
    }

    @Test
    @DisplayName("재무는 3개년이 각각 한 행이고, 다시 받으면 덮어쓴다")
    void 재무_업서트() {
        seedSamsung();
        given(dartClient.fetchAnnualFinancials(anyString(), anyInt()))
                .willReturn(List.of(
                        snapshot(2025, "300870903000000"),
                        snapshot(2024, "258935494000000"),
                        snapshot(2023, "232723000000000")));

        ingestService.ingestAnnualFinancials(2025);
        em.flush();
        ingestService.ingestAnnualFinancials(2025);
        em.flush();

        List<CorpFinancial> rows =
                corpFinancialRepository.findByStockCodeOrderByFiscalYearDescQuarterDesc(SAMSUNG);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getFiscalYear()).isEqualTo(2025);
        assertThat(rows.get(0).getRevenue()).isEqualTo(new BigInteger("300870903000000"));
        assertThat(rows.get(0).getQuarter()).isEqualTo(CorpFinancial.ANNUAL_QUARTER);
    }

    @Test
    @DisplayName("한도를 넘기면 남은 종목을 더 부르지 않는다")
    void 한도_초과는_회차를_접는다() {
        seedSamsung();
        seedKakao();
        willThrow(DartException.rateLimited()).given(dartClient).fetchCompany(anyString());

        int updated = ingestService.ingestProfiles();

        assertThat(updated).isZero();
        // 첫 종목에서 접었으므로 두 번째 종목은 부르지 않는다.
        verify(dartClient).fetchCompany(anyString());
    }

    @Test
    @DisplayName("한 종목이 실패해도 나머지는 계속 수집한다")
    void 종목_실패는_격리된다() {
        seedSamsung();
        seedKakao();
        given(dartClient.fetchCompany(SAMSUNG_CORP)).willThrow(new DartException("일시 오류"));
        given(dartClient.fetchCompany("00258801")).willReturn(company("00258801", "카카오"));

        int updated = ingestService.ingestProfiles();
        em.flush();

        assertThat(updated).isEqualTo(1);
    }

    @Test
    @DisplayName("기준 연도를 안 정하면 작년을 쓴다 — 올해 사업보고서는 아직 없다")
    void 기본_기준연도는_작년() {
        assertThat(ingestService.financialYear(LocalDate.of(2026, 9, 2))).isEqualTo(2025);
    }

    @Test
    @DisplayName("고유번호가 바뀌어도 이미 받아 둔 기업개황은 남는다")
    void 시드_재바인딩은_개황을_지우지_않는다() {
        CorpProfile profile = corpProfileRepository.save(
                CorpProfile.of(SAMSUNG, "00000001", "삼성전자"));
        profile.update("삼성전자", "SEC", "대표", "264", "수원", null, null, "19690113", "12");
        corpProfileRepository.save(profile);
        em.flush();
        em.clear();

        // 합병·재상장으로 고유번호가 바뀐 상황이다.
        given(dartClient.fetchListedCorpCodes())
                .willReturn(List.of(new CorpCodeRow(SAMSUNG_CORP, "삼성전자", SAMSUNG)));

        int changed = ingestService.seedCorpCodes();
        em.flush();
        em.clear();

        CorpProfile after = corpProfileRepository.findById(SAMSUNG).orElseThrow();
        assertThat(changed).isEqualTo(1);
        assertThat(after.getCorpCode()).isEqualTo(SAMSUNG_CORP);
        assertThat(after.getCeoName()).as("개황이 null 로 덮이면 안 된다").isEqualTo("대표");
        assertThat(after.getAddress()).isEqualTo("수원");
    }

    @Test
    @DisplayName("기준 연도가 비면 한 해 뒤로 물러선다 — 1분기 회차엔 작년 사업보고서가 아직 없다")
    void 빈_연도는_한_해_뒤로_물러선다() {
        seedSamsung();
        given(dartClient.fetchAnnualFinancials(SAMSUNG_CORP, 2026)).willReturn(List.of());
        given(dartClient.fetchAnnualFinancials(SAMSUNG_CORP, 2025))
                .willReturn(List.of(
                        snapshot(2025, "300870903000000"), snapshot(2024, "258935494000000")));

        int saved = ingestService.ingestAnnualFinancials(2026);
        em.flush();

        assertThat(saved).isEqualTo(2);
        assertThat(corpFinancialRepository.findByStockCodeOrderByFiscalYearDescQuarterDesc(SAMSUNG))
                .extracting(CorpFinancial::getFiscalYear)
                .containsExactly(2025, 2024);
    }

    // ── 픽스처 ──────────────────────────────────────────────

    private void seedSamsung() {
        corpProfileRepository.save(CorpProfile.of(SAMSUNG, SAMSUNG_CORP, "삼성전자"));
        em.flush();
    }

    private void seedKakao() {
        corpProfileRepository.save(CorpProfile.of(KAKAO, "00258801", "카카오"));
        em.flush();
    }

    private static DartDisclosure disclosure(String receiptNo, String reportName) {
        return new DartDisclosure(SAMSUNG_CORP, SAMSUNG, receiptNo, reportName, "삼성전자", "20260831");
    }

    private static DartFinancialSnapshot snapshot(int year, String revenue) {
        return new DartFinancialSnapshot(
                year,
                "CFS",
                "KRW",
                "20260310002820",
                new BigInteger(revenue),
                null,
                null,
                null,
                null,
                null);
    }

    private static DartCompany company(String corpCode, String name) {
        return new DartCompany(
                corpCode, name, null, null, "대표", "264", "주소", null, null, "19690113", "12");
    }

    /** Stock 은 ANT-DATA 담당 엔티티라 생성 팩터리를 추가하지 않고 네이티브 INSERT 로 넣는다. */
    private void insertStock(String code, String name) {
        em.createNativeQuery(
                        "INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, code)
                .setParameter(2, name)
                .executeUpdate();
    }

    /**
     * 저장이 깨지는 경우. 수집 범위가 좁아져 {@code stocks} 에서 빠진 종목의 프로필이 남아
     * 있으면 공시 저장이 FK 위반으로 터지는데, 그 예외는 {@code DartException} 이 아니라
     * 회차 전체를 끌고 내려간다. 리포지토리를 목으로 두는 이유는 실제 FK 위반이 테스트
     * 트랜잭션의 커밋 시점에야 터져 서비스 안에서 잡히지 않기 때문이다.
     */
    @Nested
    @DisplayName("저장 실패")
    class 저장_실패 {

        @MockitoBean ResearchDocumentRepository failingRepository;

        @Test
        @DisplayName("한 종목의 저장이 깨져도 남은 종목을 계속 훑는다")
        void 저장_실패는_회차를_죽이지_않는다() {
            seedSamsung();
            seedKakao();
            given(failingRepository.findAllBySourceAndExternalIdIn(any(), any()))
                    .willReturn(List.of());
            willThrow(new DataIntegrityViolationException("stocks 에 없는 종목"))
                    .given(failingRepository)
                    .saveAll(any());
            given(dartClient.fetchDisclosures(anyString(), any(), any()))
                    .willReturn(List.of(disclosure("20260831000066", "사업보고서")));

            LocalDate today = LocalDate.of(2026, 9, 2);
            int created = ingestService.ingestDisclosures(today.minusDays(7), today);

            assertThat(created).isZero();
            verify(dartClient, times(2)).fetchDisclosures(anyString(), any(), any());
        }
    }
}
