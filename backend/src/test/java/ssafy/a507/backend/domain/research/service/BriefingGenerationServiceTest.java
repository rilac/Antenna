package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import ssafy.a507.backend.common.ai.AiKeyRejectedException;
import ssafy.a507.backend.domain.research.entity.AiBriefing;
import ssafy.a507.backend.domain.research.repository.AiBriefingRepository;

/**
 * 배치 B6 의 시장 브리핑을 본다. 종목 브리핑은 2026-09-09 에 없앴다 — 이 테스트에 종목 입력이 없는
 * 이유다.
 *
 * <p>두 가지가 중심이다. <b>수치는 코드가 계산해 프롬프트에 넣는다</b> — 모델이 받은 재료에 지수
 * 등락률·상승 상위가 이미 들어 있어야 한다. <b>D16</b> — 확률·가능성 수치가 든 응답은 저장되지 않아야 한다.
 *
 * <p>GMS 는 스텁이다 — 테스트는 외부와 통신하지 않는다.
 */
@SpringBootTest(properties = {"app.ai.api-key=test-key", "app.ai.prompt-version=v1"})
@Transactional
@DisplayName("AI 시장 브리핑 생성 (B6)")
class BriefingGenerationServiceTest {

    private static final String SAMSUNG = "005930";
    private static final LocalDate TARGET = LocalDate.of(2026, 9, 2);
    private static final String REPLY = "헤드라인 한 줄\n\n본문 첫 문단.\n\n본문 둘째 문단.";

    @Autowired BriefingGenerationService generationService;
    @Autowired AiBriefingRepository aiBriefingRepository;
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
    @DisplayName("지수 등락률과 상승·하락 상위를 코드가 계산해 재료로 넣고, 첫 줄을 헤드라인으로 저장한다 — 하루 1콜")
    void 재료_주입() {
        seedQuotes(25);
        seedIndex();
        given(aiClient.complete(anyString(), anyString())).willReturn(REPLY);

        assertThat(generationService.generate()).isEqualTo(1);
        em.flush();

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiClient, times(1)).complete(anyString(), input.capture());
        assertThat(input.getValue())
                .contains("기준일: 2026-09-02")
                .contains("코스피 종가 3200", "1영업일 +0.31%", "상승 상위: 삼성전자 +0.14%")
                .doesNotContain("종목:");

        List<AiBriefing> saved = aiBriefingRepository.findAll();
        assertThat(saved).singleElement().satisfies(b -> {
            assertThat(b.getScope()).isEqualTo(AiBriefing.Scope.MARKET);
            assertThat(b.getStock()).isNull();
            assertThat(b.getTargetDate()).isEqualTo(TARGET);
            assertThat(b.getHeadline()).isEqualTo("헤드라인 한 줄");
            assertThat(b.getBody()).isEqualTo("본문 첫 문단.\n\n본문 둘째 문단.");
            assertThat(b.getPromptVersion()).isEqualTo("v1");
        });
    }

    @Test
    @DisplayName("같은 기준일·같은 세대가 이미 있으면 다시 부르지 않는다 — 주말에 도는 회차가 한도를 태우지 않는다")
    void 재실행_멱등() {
        seedQuotes(3);
        seedIndex();
        given(aiClient.complete(anyString(), anyString())).willReturn(REPLY);

        generationService.generate();
        em.flush();
        int second = generationService.generate();

        assertThat(second).isZero();
        verify(aiClient, times(1)).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("세대가 뒤처진 행은 제자리에서 다시 쓴다 — id 가 유지된다")
    void 세대_재생성() {
        seedQuotes(3);
        seedIndex();
        AiBriefing old = aiBriefingRepository.save(AiBriefing.of(
                AiBriefing.Scope.MARKET, null, TARGET, "옛 시장", "옛 본문", "v0"));
        em.flush();
        given(aiClient.complete(anyString(), anyString())).willReturn(REPLY);

        assertThat(generationService.generate()).isEqualTo(1);
        em.flush();

        assertThat(aiBriefingRepository.findAll()).singleElement().satisfies(b -> {
            assertThat(b.getId()).isEqualTo(old.getId());
            assertThat(b.getHeadline()).isEqualTo("헤드라인 한 줄");
            assertThat(b.getPromptVersion()).isEqualTo("v1");
        });
    }

    @Test
    @DisplayName("D16 — 확률·가능성 수치가 든 응답은 버린다")
    void D16_가드() {
        seedQuotes(3);
        seedIndex();
        given(aiClient.complete(anyString(), anyString())).willReturn("제목\n\n단기 하락 68% 전망이다.");

        assertThat(generationService.generate()).isZero();
        assertThat(aiBriefingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("D16 패턴 — 방향+확률, 확률+수치, 방향+수치는 잡고, 사실 서술과 뉴스 속 '확률'은 통과시킨다")
    void D16_패턴() {
        var p = BriefingGenerationService.FORBIDDEN;
        assertThat(p.matcher("상승 확률이 높다").find()).isTrue();
        assertThat(p.matcher("오를 가능성이 60%다").find()).isTrue();
        assertThat(p.matcher("반등할 가능성은 커 보인다").find()).isTrue();
        assertThat(p.matcher("확률 70%").find()).isTrue();
        assertThat(p.matcher("하락 68%").find()).isTrue();
        assertThat(p.matcher("5영업일간 3.2% 하락했다").find()).isFalse();
        assertThat(p.matcher("부채비율은 50.0%다").find()).isFalse();
        assertThat(p.matcher("금리 인하 확률이 높아졌다는 보도가 있었다").find()).isFalse();
        assertThat(p.matcher("확인해 볼 가능성 2가지가 있다").find()).isFalse();
    }

    @Test
    @DisplayName("본문 없이 헤드라인 한 줄만 오면 버린다 — 저장하면 현 세대로 굳어 다시 안 만든다")
    void 본문_없음() {
        seedQuotes(3);
        seedIndex();
        given(aiClient.complete(anyString(), anyString())).willReturn("헤드라인만 왔다.");

        assertThat(generationService.generate()).isZero();
        assertThat(aiBriefingRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("기준일 지수가 없으면 부르지 않는다 — 재료 없이 만들면 지어낸 글이다")
    void 지수_없음() {
        seedQuotes(3);

        assertThat(generationService.generate()).isZero();
        verify(aiClient, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("일봉이 하나도 없으면 기준일이 없어 부르지 않는다")
    void 시세_없음() {
        assertThat(generationService.generate()).isZero();
        verify(aiClient, never()).complete(anyString(), anyString());
    }

    @Test
    @DisplayName("GMS 가 키를 거부해도 회차는 조용히 끝난다 — 저장 없음, 다음 회차가 다시 시도한다")
    void 키_거부() {
        seedQuotes(3);
        seedIndex();
        given(aiClient.complete(anyString(), anyString()))
                .willThrow(new AiKeyRejectedException("GMS 키 거부(401) — no token left"));

        assertThat(generationService.generate()).isZero();
        assertThat(aiBriefingRepository.findAll()).isEmpty();
    }

    // ── 재료 ─────────────────────────────────────────────────

    /** 종가 70000 부터 하루 100원씩 오르는 {@code days}개 일봉. 마지막 날이 기준일이다. */
    private void seedQuotes(int days) {
        for (int i = 0; i < days; i++) {
            LocalDate date = TARGET.minusDays(days - 1 - i);
            em.createNativeQuery(
                            "INSERT INTO daily_quotes (stock_code, trade_date, close, volume, collected_at)"
                                    + " VALUES (?, ?, ?, ?, ?)")
                    .setParameter(1, SAMSUNG)
                    .setParameter(2, date)
                    .setParameter(3, new BigDecimal(70000 + i * 100))
                    .setParameter(4, 10_000_000L)
                    .setParameter(5, Instant.now())
                    .executeUpdate();
        }
        em.flush();
    }

    /** 코스피 6점 — 3150 에서 기준일 3200 까지. 1영업일 +0.31%. */
    private void seedIndex() {
        for (int i = 0; i < 6; i++) {
            em.createNativeQuery("INSERT INTO index_quotes (index_code, trade_date, close) VALUES ('KOSPI', ?, ?)")
                    .setParameter(1, TARGET.minusDays(5 - i))
                    .setParameter(2, new BigDecimal(3150 + i * 10))
                    .executeUpdate();
        }
        em.flush();
    }
}
