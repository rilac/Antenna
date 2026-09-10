package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.ai.GpuAiClient;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.NewsSignalRepository;

/**
 * 자체 서빙(교내 GPU) 을 실제로 부른다 — 기사 수만큼 콜이 나간다. 운영 DB 는 건드리지 않는다.
 *
 * <p>확인하려는 것은 파서가 아니라 <b>판정 품질</b>이다. 특히 경계 세 건 — 구단 매각·중계권 확보는
 * 스포츠가 소재여도 회사의 사업 결정이라 "관련" 이어야 하고, 이름만 겹치는 지역 행사는 스포츠 어휘가
 * 하나도 없어도 "무관" 이어야 한다. 이 셋을 못 가르면 정규식({@code NewsIngestService.isNoise})과
 * 다를 것이 없어 이 기능을 만든 이유가 사라진다.
 *
 * <p>{@code app.ai.gpu} 설정이 없으면 조용히 건너뛴다. 터널이 뜬 동안 사람이 시킬 때만 돈다 —
 * {@code --tests '*E2ELiveTest*' --rerun}.
 *
 * <p>Gradle 은 표준출력을 콘솔에 흘리지 않는다. 결과는
 * {@code build/test-results/test/TEST-*.xml} 에서 {@code [E2E]} 로 찾는다.
 */
@SpringBootTest(
        properties = {
            "app.ai.gpu.api-key=${AI_GPU_API_KEY:}",
            "app.ai.gpu.base-url=${AI_GPU_BASE_URL:}",
            "app.ai.gpu.model=${AI_GPU_MODEL:antenna}"
        })
@Transactional
@DisplayName("뉴스 관련도 판정 실호출 E2E")
class RelevanceE2ELiveTest {

    /**
     * @param regexDrops 스포츠 어휘 정규식이 버리는 기사인가 — 판정이 정규식을 이겨야 하는 자리를 표시한다
     */
    private record Case(String code, String company, String title, String snippet, boolean relevant, boolean regexDrops) {}

    private static final List<Case> CASES = List.of(
            new Case("000150", "두산", "두산 선발 최승용, 3이닝 4실점으로 강판", "대전 한화전에서 무너졌다", false, true),
            new Case("000880", "한화", "한화, 9회말 끝내기 홈런으로 3연승", "선두 추격", false, true),
            new Case("003470", "유안타증권", "유안타증권 오픈 골프대회 개막", "총상금 12억원", false, true),
            new Case("034020", "두산에너빌리티", "두산에너빌리티, 체코 원전 최종 계약", "약 10조원 규모", true, false),
            new Case("036570", "엔씨소프트", "엔씨소프트, NC다이노스 지분 매각 검토", "구단 운영 부담", true, true),
            new Case("035760", "CJ ENM", "CJ ENM, 프로야구 중계권 3년 확보", "유료 구독 확대 노림", true, true),
            new Case("000660", "SK하이닉스", "충북도, 알츠하이머 검진 협약 체결", "도민 대상 무료 검진", false, false),
            new Case("000270", "기아", "기아, 3분기 영업이익 2조원 돌파", "북미 판매 호조", true, false),
            new Case("011170", "롯데케미칼", "롯데케미칼, 여수공장 가동 중단", "정기 보수 돌입", true, false));

    @Autowired NewsRelevanceService relevanceService;
    @Autowired NewsSignalRepository newsSignalRepository;
    @Autowired StockRepository stockRepository;
    @Autowired GpuAiClient gpuAiClient;
    @Autowired EntityManager em;

    @Test
    @DisplayName("이름만 겹치는 소식과 회사의 사업 결정을 가른다")
    void 판정_품질() {
        assumeThat(gpuAiClient.isConfigured()).as("app.ai.gpu 설정이 없어 건너뛴다").isTrue();

        Map<Long, Case> seeded = new LinkedHashMap<>();
        CASES.forEach(c -> seeded.put(seed(c), c));

        long started = System.currentTimeMillis();
        int judged = relevanceService.judgeRecent();
        em.flush();
        long elapsed = System.currentTimeMillis() - started;

        System.out.printf(
                "[E2E] 모델 %s · %d/%d건 판정 · %.1f초 (건당 %.1f초)%n",
                gpuAiClient.model(), judged, CASES.size(), elapsed / 1000.0, elapsed / 1000.0 / CASES.size());

        Map<Long, Boolean> verdicts = new LinkedHashMap<>();
        newsSignalRepository
                .findByDocument_IdIn(seeded.keySet())
                .forEach(s -> verdicts.put(s.getDocument().getId(), s.isRelevant()));

        seeded.forEach((id, c) -> {
            Boolean actual = verdicts.get(id);
            System.out.printf(
                    "[E2E] %s  기대=%s  판정=%s%s  |  %s (%s)%n",
                    Boolean.valueOf(c.relevant()).equals(actual) ? "OK  " : "틀림",
                    label(c.relevant()),
                    actual == null ? "없음" : label(actual),
                    c.regexDrops() ? "  [정규식이 버리는 기사]" : "",
                    c.title(),
                    c.company());
        });

        assertThat(judged).as("판정을 읽지 못한 건이 있다 — 응답 모양을 [E2E] 로그로 확인한다").isEqualTo(CASES.size());
        seeded.forEach((id, c) -> assertThat(verdicts.get(id))
                .as("%s · %s", c.company(), c.title())
                .isEqualTo(c.relevant()));
    }

    private static String label(boolean relevant) {
        return relevant ? "관련" : "무관";
    }

    private Long seed(Case c) {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, c.code())
                .setParameter(2, c.company())
                .executeUpdate();
        em.flush();
        ResearchDocument document = ResearchDocument.collected(
                stockRepository.getReferenceById(c.code()),
                ResearchDocument.Source.NEWS,
                "live-" + c.code(),
                c.title(),
                "https://example.com/live-" + c.code(),
                c.snippet(),
                Instant.now().minus(1, ChronoUnit.DAYS));
        em.persist(document);
        em.flush();
        return document.getId();
    }
}
