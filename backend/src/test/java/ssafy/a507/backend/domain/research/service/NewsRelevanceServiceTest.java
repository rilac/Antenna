package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.common.ai.GpuAiClient;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.NewsSignalRepository;

/**
 * 뉴스 관련도 판정 (ANT-RESEARCH-02).
 *
 * <p>중심은 셋이다. <b>이름만 같은 소식을 가려낸다</b> — 두산·한화·KT 는 KBO 구단 이름이기도 하다.
 * <b>건 단위로 넘어간다</b> — 기사 하나의 실패가 회차를 접지 않는다. <b>멱등</b> — 같은 세대로
 * 판정한 기사는 다시 부르지 않는다.
 *
 * <p>GPU 는 스텁이다 — 테스트는 외부와 통신하지 않는다.
 */
@SpringBootTest
@Transactional
@DisplayName("뉴스 관련도 판정")
class NewsRelevanceServiceTest {

    private static final String DOOSAN = "000150";
    private static final String BALL = "두산 선발 최승용, 3이닝 4실점으로 강판";
    private static final String BIZ = "두산에너빌리티, 체코 원전 수주";

    @Autowired NewsRelevanceService relevanceService;
    @Autowired NewsSignalRepository newsSignalRepository;
    @Autowired StockRepository stockRepository;
    @Autowired EntityManager em;

    @MockitoBean GpuAiClient gpuAiClient;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, DOOSAN)
                .setParameter(2, "두산")
                .executeUpdate();
        em.flush();
        given(gpuAiClient.isConfigured()).willReturn(true);
        given(gpuAiClient.model()).willReturn("antenna");
        given(gpuAiClient.promptVersion()).willReturn("v1");
    }

    @Test
    @DisplayName("회사 이름만 같은 기사를 무관으로 남긴다 — 재료 선정이 이 값을 읽는다")
    void 판정_저장() {
        Long ball = seedNews("n-ball", BALL, "강판당했다");
        Long biz = seedNews("n-biz", BIZ, "10조원 규모");
        givenJudgeByKeyword();

        assertThat(relevanceService.judgeRecent()).isEqualTo(2);
        em.flush();

        assertThat(newsSignalRepository.findByDocument_Id(ball))
                .get()
                .satisfies(s -> {
                    assertThat(s.isRelevant()).isFalse();
                    assertThat(s.getModel()).isEqualTo("antenna");
                    assertThat(s.getPromptVersion()).isEqualTo("v1");
                });
        assertThat(newsSignalRepository.findByDocument_Id(biz))
                .get()
                .satisfies(s -> assertThat(s.isRelevant()).isTrue());
    }

    @Test
    @DisplayName("회사 이름과 기사를 함께 물어본다 — 같은 제목도 회사가 다르면 답이 달라진다")
    void 재료() {
        seedNews("n-ball", BALL, "강판당했다");
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("무관");

        relevanceService.judgeRecent();

        verify(gpuAiClient).complete(NewsRelevanceService.INSTRUCTION, "회사: 두산\n기사: " + BALL + " · 강판당했다");
    }

    @Test
    @DisplayName("app.ai.gpu 설정이 없으면 부르지 않는다 — 재료 선정은 정규식으로 떨어진다")
    void 설정_없음() {
        given(gpuAiClient.isConfigured()).willReturn(false);
        seedNews("n-ball", BALL, "강판당했다");

        assertThat(relevanceService.judgeRecent()).isZero();

        verify(gpuAiClient, never()).complete(anyString(), anyString());
        assertThat(newsSignalRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("같은 세대로 판정한 기사는 다시 부르지 않는다")
    void 멱등() {
        seedNews("n-biz", BIZ, "10조원 규모");
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("관련");

        assertThat(relevanceService.judgeRecent()).isEqualTo(1);
        em.flush();
        assertThat(relevanceService.judgeRecent()).as("두 번째 회차는 대상이 없다").isZero();

        verify(gpuAiClient, times(1)).complete(anyString(), anyString());
        assertThat(newsSignalRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("지시문 세대가 올라가면 옛 판정을 덮어쓴다 — 행이 늘지 않는다")
    void 세대_교체() {
        Long biz = seedNews("n-biz", BIZ, "10조원 규모");
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("무관");
        relevanceService.judgeRecent();
        em.flush();

        given(gpuAiClient.promptVersion()).willReturn("v2");
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("관련");
        assertThat(relevanceService.judgeRecent()).isEqualTo(1);
        em.flush();

        assertThat(newsSignalRepository.findAll()).hasSize(1);
        assertThat(newsSignalRepository.findByDocument_Id(biz)).get().satisfies(s -> {
            assertThat(s.isRelevant()).isTrue();
            assertThat(s.getPromptVersion()).isEqualTo("v2");
        });
    }

    @Test
    @DisplayName("한 건이 실패해도 회차를 접지 않는다")
    void 건별_실패() {
        seedNews("n-ball", BALL, "강판당했다");
        Long biz = seedNews("n-biz", BIZ, "10조원 규모");
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("관련");
        willThrow(new AiException("타임아웃"))
                .given(gpuAiClient)
                .complete(anyString(), contains("3이닝"));

        assertThat(relevanceService.judgeRecent()).isEqualTo(1);
        em.flush();

        assertThat(newsSignalRepository.findAll())
                .singleElement()
                .satisfies(s -> assertThat(s.getDocument().getId()).isEqualTo(biz));
    }

    @Test
    @DisplayName("판정을 읽지 못한 건은 남기지 않는다 — 다음 회차가 다시 집는다")
    void 못_읽은_답() {
        seedNews("n-biz", BIZ, "10조원 규모");
        given(gpuAiClient.complete(anyString(), anyString())).willReturn("음, 글쎄요.");

        assertThat(relevanceService.judgeRecent()).isZero();

        assertThat(newsSignalRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("부정형을 반대로 읽지 않는다 — \"관련 없음\" 에도 \"관련\" 이 들어 있다")
    void 답_해석() {
        assertThat(NewsRelevanceService.parse("관련")).isTrue();
        assertThat(NewsRelevanceService.parse(" 관련\n")).isTrue();
        assertThat(NewsRelevanceService.parse("판정: 관련")).isTrue();
        assertThat(NewsRelevanceService.parse("무관")).isFalse();
        assertThat(NewsRelevanceService.parse("관련 없음")).isFalse();
        assertThat(NewsRelevanceService.parse("이 기사는 회사와 관련없다")).isFalse();
        assertThat(NewsRelevanceService.parse("모르겠다")).isNull();
        assertThat(NewsRelevanceService.parse(null)).isNull();
    }

    // ── 재료 ─────────────────────────────────────────────────

    /** 야구 기사면 "무관", 아니면 "관련". 재료에 기사 제목이 들어가는 것을 함께 확인한다. */
    private void givenJudgeByKeyword() {
        given(gpuAiClient.complete(anyString(), anyString()))
                .willAnswer(call -> call.<String>getArgument(1).contains("3이닝") ? "무관" : "관련");
    }

    private Long seedNews(String externalId, String title, String snippet) {
        ResearchDocument document = ResearchDocument.collected(
                stockRepository.getReferenceById(DOOSAN),
                ResearchDocument.Source.NEWS,
                externalId,
                title,
                "https://example.com/" + externalId,
                snippet,
                Instant.now().minus(1, ChronoUnit.DAYS));
        em.persist(document);
        em.flush();
        return document.getId();
    }
}
