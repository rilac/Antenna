package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.entity.NewsSignal;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;
import ssafy.a507.backend.domain.research.repository.ResearchPointRepository;

/**
 * 배치 B6 의 포인트 갈래를 본다 (ANT-RESEARCH-04).
 *
 * <p>중심은 셋이다. <b>재료에 문서 번호가 들어간다</b> — 모델이 근거를 지목할 수 있어야 한다.
 * <b>건 단위로 버린다</b> — 규칙을 어긴 한 건 때문에 나머지 여덟 건을 잃지 않는다. <b>멱등</b> —
 * 같은 종목·같은 기준일이면 다시 부르지 않는다.
 *
 * <p>GMS 는 스텁이다 — 테스트는 외부와 통신하지 않는다.
 */
@SpringBootTest(properties = "app.ai.api-key=test-key")
@Transactional
@DisplayName("리서치 포인트 생성 (요청 시점)")
class ResearchPointGenerationServiceTest {

    private static final String SAMSUNG = "005930";
    private static final LocalDate TARGET = LocalDate.of(2026, 9, 2);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired ResearchPointGenerationService generationService;
    @Autowired ResearchPointRepository researchPointRepository;
    @Autowired StockRepository stockRepository;
    @Autowired EntityManager em;

    @MockitoBean AiClient aiClient;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, SAMSUNG)
                .setParameter(2, "삼성전자")
                .executeUpdate();
        em.flush();
    }

    @Test
    @DisplayName("수치와 문서 번호를 재료로 넣고, JSON 배열을 3열로 저장한다")
    void 생성() {
        seedQuotes(25);
        Long newsId = seedDocument(ResearchDocument.Source.NEWS, "n-1", "제목", "신제품 양산 소식.", TARGET.minusDays(1));
        Long dartId = seedDocument(ResearchDocument.Source.DART, "d-1", "반기보고서", null, TARGET.minusDays(2));
        given(aiClient.complete(anyString(), anyString())).willReturn(reply(newsId, dartId));

        assertThat(generationService.generate(stockRepository.getReferenceById(SAMSUNG))).isEqualTo(3);
        em.flush();

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiClient).complete(anyString(), input.capture());
        assertThat(input.getValue())
                .contains("종목: 삼성전자 (005930)")
                .contains("종가 72400원")
                .contains("52주 범위 내 위치 100% (0%=최저, 100%=최고)")
                .contains("문서(번호 · 날짜 · 원천 · 내용):")
                .contains("[" + newsId + "] 2026-09-01 · 뉴스 · 제목 · 신제품 양산 소식.")
                .as("공시는 발췌가 없어 보고서명만 재료로 쓴다")
                .contains("[" + dartId + "] 2026-08-31 · 공시 · 반기보고서");

        List<ResearchPoint> saved = researchPointRepository.findAll();
        assertThat(saved).hasSize(3).allSatisfy(p -> {
            assertThat(p.getStock().getCode()).isEqualTo(SAMSUNG);
            assertThat(p.getTargetDate()).isEqualTo(TARGET);
        });
        assertThat(saved).filteredOn(p -> p.getKind() == ResearchPoint.Kind.POSITIVE)
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.getBody()).isEqualTo("신제품 양산이 시작됐다.");
                    assertThat(p.getDocument().getId()).isEqualTo(newsId);
                });
        assertThat(saved).filteredOn(p -> p.getKind() == ResearchPoint.Kind.RISK)
                .singleElement()
                .satisfies(p -> assertThat(p.getDocument().getId()).isEqualTo(dartId));
        assertThat(saved).filteredOn(p -> p.getKind() == ResearchPoint.Kind.CHECK)
                .singleElement()
                .satisfies(p -> assertThat(p.getDocument()).as("수치에서 나온 종합 포인트").isNull());
    }

    @Test
    @DisplayName("코드펜스·군말이 붙어 와도 배열만 읽는다")
    void 코드펜스() {
        seedQuotes(3);
        given(aiClient.complete(anyString(), anyString()))
                .willReturn("```json\n[{\"kind\":\"CHECK\",\"body\":\"거래량을 지켜볼 만하다.\",\"documentId\":null}]\n```");

        assertThat(generationService.generate(stockRepository.getReferenceById(SAMSUNG))).isEqualTo(1);
    }

    @Test
    @DisplayName("규칙을 어긴 건만 버리고 나머지는 저장한다 — kind 오류·빈 body·300자 초과·D16·목록 밖 documentId·입력에 없는 숫자")
    void 건별_버리기() {
        seedQuotes(3);
        Long newsId = seedDocument(ResearchDocument.Source.NEWS, "n-1", "제목", "요약.", TARGET.minusDays(1));
        given(aiClient.complete(anyString(), anyString())).willReturn("""
                [
                  {"kind": "GOOD", "body": "kind 가 어휘 밖이다.", "documentId": null},
                  {"kind": "POSITIVE", "body": "   ", "documentId": null},
                  {"kind": "RISK", "body": "%s", "documentId": null},
                  {"kind": "RISK", "body": "단기 하락 68%% 전망이다.", "documentId": null},
                  {"kind": "CHECK", "body": "없는 문서를 지목했다.", "documentId": 999999},
                  {"kind": "CHECK", "body": "순이익이 999억원이다.", "documentId": null},
                  {"kind": "POSITIVE", "body": "종가 70,200원으로 살아남는 유일한 건이다.", "documentId": %d}
                ]
                """.formatted("가".repeat(301), newsId));

        assertThat(generationService.generate(stockRepository.getReferenceById(SAMSUNG))).isEqualTo(1);
        em.flush();

        assertThat(researchPointRepository.findAll()).singleElement().satisfies(p -> {
            assertThat(p.getKind()).isEqualTo(ResearchPoint.Kind.POSITIVE);
            assertThat(p.getBody()).as("쉼표 붙은 숫자도 입력의 70200 과 같은 값으로 본다").isEqualTo("종가 70,200원으로 살아남는 유일한 건이다.");
            assertThat(p.getDocument().getId()).isEqualTo(newsId);
        });
    }

    @Test
    @DisplayName("documentId 를 문자열·실수로 싸서 보내도 번호로 읽는다 — 근거 링크를 잃지 않는다")
    void documentId_모양() {
        seedQuotes(3);
        Long newsId = seedDocument(ResearchDocument.Source.NEWS, "n-1", "제목", "요약.", TARGET.minusDays(1));
        given(aiClient.complete(anyString(), anyString())).willReturn("""
                [
                  {"kind": "POSITIVE", "body": "문자열로 왔다.", "documentId": "%d"},
                  {"kind": "RISK", "body": "실수로 왔다.", "documentId": %d.0},
                  {"kind": "CHECK", "body": "번호가 아닌 문자열이다.", "documentId": "없음"}
                ]
                """.formatted(newsId, newsId));

        assertThat(generationService.generate(stockRepository.getReferenceById(SAMSUNG))).isEqualTo(3);
        em.flush();

        List<ResearchPoint> saved = researchPointRepository.findAll();
        assertThat(saved).filteredOn(p -> p.getKind() != ResearchPoint.Kind.CHECK)
                .hasSize(2)
                .allSatisfy(p -> assertThat(p.getDocument().getId()).isEqualTo(newsId));
        assertThat(saved).filteredOn(p -> p.getKind() == ResearchPoint.Kind.CHECK)
                .singleElement()
                .satisfies(p -> assertThat(p.getDocument()).as("번호로 못 읽으면 종합 포인트").isNull());
    }

    @Test
    @DisplayName("한 열에 세 건까지만 담는다 — 화면이 카드 3장이다")
    void 열당_상한() {
        seedQuotes(3);
        given(aiClient.complete(anyString(), anyString())).willReturn("""
                [
                  {"kind": "RISK", "body": "첫째.", "documentId": null},
                  {"kind": "RISK", "body": "둘째.", "documentId": null},
                  {"kind": "RISK", "body": "셋째.", "documentId": null},
                  {"kind": "RISK", "body": "넷째.", "documentId": null}
                ]
                """);

        assertThat(generationService.generate(stockRepository.getReferenceById(SAMSUNG))).isEqualTo(3);
    }

    @Test
    @DisplayName("배열이 아니거나 쓸 건이 없으면 저장하지 않는다 — 다음 회차가 다시 만든다")
    void 파싱_실패() {
        seedQuotes(3);
        given(aiClient.complete(anyString(), anyString())).willReturn("포인트를 만들 수 없습니다.");

        assertThat(generationService.generate(stockRepository.getReferenceById(SAMSUNG))).isZero();
        assertThat(researchPointRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("GMS 실패는 그대로 올린다 — 요청 경로라 삼키면 화면이 빈 3열을 '없음'으로 읽는다")
    void 실패_전파() {
        seedQuotes(3);
        given(aiClient.complete(anyString(), anyString())).willThrow(new AiException("GMS 오류"));

        assertThatThrownBy(() -> generationService.generate(stockRepository.getReferenceById(SAMSUNG)))
                .isInstanceOf(AiException.class);
        assertThat(researchPointRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("같은 종목·같은 기준일 포인트가 있으면 다시 부르지 않는다")
    void 재실행_멱등() {
        seedQuotes(3);
        given(aiClient.complete(anyString(), anyString())).willReturn(reply(null, null));

        generationService.generate(stockRepository.getReferenceById(SAMSUNG));
        em.flush();
        int second = generationService.generate(stockRepository.getReferenceById(SAMSUNG));

        assertThat(second).isZero();
        verify(aiClient, times(1)).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("기준일 뒤에 나온 문서는 재료에 넣지 않는다")
    void 문서_기준일_경계() {
        seedQuotes(3);
        seedDocument(ResearchDocument.Source.NEWS, "n-old", "제목", "기준일 기사 요약.", TARGET);
        seedDocument(ResearchDocument.Source.NEWS, "n-new", "제목", "다음 날 기사 요약.", TARGET.plusDays(1));
        given(aiClient.complete(anyString(), anyString())).willReturn(reply(null, null));

        generationService.generate(stockRepository.getReferenceById(SAMSUNG));

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiClient).complete(anyString(), input.capture());
        assertThat(input.getValue()).contains("기준일 기사 요약.").doesNotContain("다음 날 기사 요약.");
    }

    @Test
    @DisplayName("숫자 가드는 단위를 바꿔 쓴 것도 잡는다 — 340000 이 입력에 있다고 340 이 통과하면 안 된다")
    void 숫자_가드() {
        String input = "연간 재무(CFS, 억원): 2025년 매출 3000000 영업이익 320000 순이익 340000\n"
                + "종가 70200원 · 1영업일 -1.90% · 20영업일 +9.73%\n부채비율 28.0% · 기준일: 2026-09-04";

        // 단위 오독 — 이 가드를 만든 계기다. 부분 문자열로 보면 340000 안의 340 이 통과한다.
        assertThat(ResearchPointGenerationService.numbersGrounded("순이익이 340억원이다.", input)).isFalse();
        assertThat(ResearchPointGenerationService.numbersGrounded("순이익이 34억원이다.", input)).isFalse();
        // 아예 없는 수치
        assertThat(ResearchPointGenerationService.numbersGrounded("순이익이 999억원이다.", input)).isFalse();
        // 반올림 — 9.73 을 9.7 로 줄이면 다른 값이다
        assertThat(ResearchPointGenerationService.numbersGrounded("20영업일 9.7% 올랐다.", input)).isFalse();

        // 그대로 옮긴 값은 통과한다
        assertThat(ResearchPointGenerationService.numbersGrounded("순이익이 340000억원이다.", input)).isTrue();
        assertThat(ResearchPointGenerationService.numbersGrounded("종가 70,200원이다.", input)).isTrue();
        assertThat(ResearchPointGenerationService.numbersGrounded("1영업일 1.90% 하락했다.", input)).isTrue();
        // 꼬리 0 은 값이 같다 — 1.90 과 1.9
        assertThat(ResearchPointGenerationService.numbersGrounded("1영업일 1.9% 하락했다.", input)).isTrue();
        // 소수점 표기 차이는 같은 값이다 — 28.0 과 28
        assertThat(ResearchPointGenerationService.numbersGrounded("부채비율이 28%다.", input)).isTrue();
        // 날짜를 우리말로 옮겨도 같은 값이다 — 2026-09-04 와 9월 4일
        assertThat(ResearchPointGenerationService.numbersGrounded("2026년 9월 4일 기준이다.", input)).isTrue();
        // 숫자가 없는 문장
        assertThat(ResearchPointGenerationService.numbersGrounded("원전 수주 소식이 있었다.", input)).isTrue();
    }

    @Test
    @DisplayName("스포츠 기사는 재료에서 뺀다 — 야구 기사가 '위험 요인'으로 올라간 적이 있다")
    void 스포츠_기사_제외() {
        seedQuotes(3);
        seedDocument(ResearchDocument.Source.NEWS, "n-ball", "두산 선발 최승용 3이닝 4실점", "강판당했다", TARGET.minusDays(1));
        seedDocument(ResearchDocument.Source.NEWS, "n-biz", "두산, 협동로봇 수주", "북미 공급 계약", TARGET.minusDays(1));
        given(aiClient.complete(anyString(), anyString())).willReturn("[]");

        generationService.generate(stockRepository.getReferenceById(SAMSUNG));

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiClient).complete(anyString(), input.capture());
        assertThat(input.getValue()).contains("협동로봇 수주").doesNotContain("3이닝");
    }

    @Test
    @DisplayName("관련도 판정이 정규식을 이긴다 — 스포츠가 소재여도 회사의 사업 결정이면 재료다")
    void 판정_우선() {
        seedQuotes(3);
        // 정규식만 보면 "프로야구" 때문에 버려진다. 판정이 관련이라고 하면 쓴다.
        Long biz = seedDocument(
                ResearchDocument.Source.NEWS, "n-rights", "CJ ENM, 프로야구 중계권 확보", "3년 계약", TARGET.minusDays(1));
        seedSignal(biz, true);
        given(aiClient.complete(anyString(), anyString())).willReturn("[]");

        generationService.generate(stockRepository.getReferenceById(SAMSUNG));

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiClient).complete(anyString(), input.capture());
        assertThat(input.getValue()).contains("중계권 확보");
    }

    @Test
    @DisplayName("무관 판정을 받은 기사는 정규식이 놓쳐도 뺀다")
    void 판정_제외() {
        seedQuotes(3);
        // 어휘로는 사업 기사와 구별되지 않는다 — 판정이 없으면 그대로 재료가 됐다.
        Long noise = seedDocument(
                ResearchDocument.Source.NEWS, "n-town", "충북도, 알츠하이머 검진 협약", "도민 대상", TARGET.minusDays(1));
        seedSignal(noise, false);
        Long biz = seedDocument(
                ResearchDocument.Source.NEWS, "n-biz", "삼성전자, 신제품 양산", "가동 시작", TARGET.minusDays(1));
        seedSignal(biz, true);
        given(aiClient.complete(anyString(), anyString())).willReturn("[]");

        generationService.generate(stockRepository.getReferenceById(SAMSUNG));

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiClient).complete(anyString(), input.capture());
        assertThat(input.getValue()).contains("신제품 양산").doesNotContain("알츠하이머");
    }

    @Test
    @DisplayName("기준일 시세가 없으면 부르지 않는다 — 재료 없이 만들면 지어낸 포인트다")
    void 시세_없음() {
        assertThat(generationService.generate(stockRepository.getReferenceById(SAMSUNG))).isZero();
        verify(aiClient, never()).complete(anyString(), anyString());
    }

    // ── 재료 ─────────────────────────────────────────────────

    /** 긍정·위험·확인 한 건씩. {@code newsId}·{@code dartId} 가 null 이면 근거 없는 종합 포인트다. */
    private static String reply(Long newsId, Long dartId) {
        return """
                [
                  {"kind": "POSITIVE", "body": "신제품 양산이 시작됐다.", "documentId": %s},
                  {"kind": "RISK", "body": "차입금이 늘었다.", "documentId": %s},
                  {"kind": "CHECK", "body": "거래량이 평소보다 많다.", "documentId": null}
                ]
                """.formatted(newsId == null ? "null" : newsId, dartId == null ? "null" : dartId);
    }

    /** 종가 70000 부터 하루 100원씩 오르는 {@code days}개 일봉. 마지막 날이 기준일이다. */
    private void seedQuotes(int days) {
        for (int i = 0; i < days; i++) {
            em.createNativeQuery(
                            "INSERT INTO daily_quotes (stock_code, trade_date, close, volume, collected_at)"
                                    + " VALUES (?, ?, ?, ?, ?)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, TARGET.minusDays(days - 1 - i))
                    .setParameter(3, new BigDecimal(70000 + i * 100))
                    .setParameter(4, 10_000_000L)
                    .setParameter(5, Instant.now())
                    .executeUpdate();
        }
        em.flush();
    }

    /** 관련도 판정 한 줄. GPU 배치가 남긴 것과 같은 모양이다. */
    private void seedSignal(Long documentId, boolean relevant) {
        em.persist(NewsSignal.judged(em.getReference(ResearchDocument.class, documentId), relevant, "antenna", "v1"));
        em.flush();
    }

    /** 그날 15시(KST) 발행. {@code snippet} 이 null 이면 발췌 없는 문서(공시)다. */
    private Long seedDocument(
            ResearchDocument.Source source, String externalId, String title, String snippet, LocalDate publishedOn) {
        ResearchDocument document = ResearchDocument.collected(
                stockRepository.getReferenceById(SAMSUNG),
                source,
                externalId,
                title,
                "https://example.com/" + externalId,
                snippet,
                publishedOn.atTime(15, 0).atZone(KST).toInstant());
        em.persist(document);
        em.flush();
        return document.getId();
    }
}
