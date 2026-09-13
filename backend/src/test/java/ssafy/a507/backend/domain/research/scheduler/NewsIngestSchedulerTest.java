package ssafy.a507.backend.domain.research.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.a507.backend.domain.research.service.BriefingGenerationService;
import ssafy.a507.backend.domain.research.service.NewsIngestService;
import ssafy.a507.backend.domain.research.service.NewsRelevanceService;

/**
 * 관련도 판정이 스케줄러 스레드를 붙잡지 않는지 본다.
 *
 * <p>앱의 {@code @Scheduled} 는 스레드 하나를 나눠 쓴다(풀 크기 기본값 1). 판정 한 회차는 첫날 밤
 * 40분쯤 걸리는데, 그동안 스케줄러 스레드를 쥐고 있으면 5초마다 도는 체인 인덱서가 통째로 멈춘다.
 * 그래서 판정은 전용 스레드로 넘기고 스케줄러 스레드는 곧바로 돌려준다.
 */
@DisplayName("뉴스 스케줄러 — 관련도 판정 전용 스레드")
class NewsIngestSchedulerTest {

    private static final long WAIT_SECONDS = 2;

    private NewsRelevanceService relevance;
    private NewsIngestScheduler scheduler;

    @BeforeEach
    void setUp() {
        relevance = mock(NewsRelevanceService.class);
        scheduler = new NewsIngestScheduler(
                mock(NewsIngestService.class), mock(BriefingGenerationService.class), relevance);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownRelevance();
    }

    @Test
    @DisplayName("판정이 끝나기 전에 스케줄러 스레드를 돌려준다 — 판정은 전용 스레드에서 돈다")
    void 스케줄러_스레드를_붙잡지_않는다() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> runningOn = new AtomicReference<>();
        willAnswer(invocation -> {
            runningOn.set(Thread.currentThread().getName());
            started.countDown();
            release.await(WAIT_SECONDS, TimeUnit.SECONDS);
            return 0;
        }).given(relevance).judgeRecent();

        long before = System.nanoTime();
        scheduler.judgeNewsRelevance();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

        // 판정이 아직 끝나지 않았는데(release 전) 호출이 이미 돌아와 있어야 한다.
        assertThat(release.getCount()).as("판정은 아직 진행 중이다").isEqualTo(1);
        assertThat(elapsedMillis).as("스케줄러 스레드를 붙잡지 않는다").isLessThan(500);
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("판정이 시작됐다").isTrue();
        assertThat(runningOn.get()).isEqualTo(NewsIngestScheduler.RELEVANCE_THREAD);
        assertThat(runningOn.get()).isNotEqualTo(Thread.currentThread().getName());

        release.countDown();
    }

    @Test
    @DisplayName("한 회차가 예외로 끝나도 다음 회차는 돈다 — 전용 스레드가 죽지 않는다")
    void 예외가_나도_다음_회차는_돈다() {
        willThrow(new IllegalStateException("터널이 끊겼다"))
                .willReturn(0)
                .given(relevance).judgeRecent();

        scheduler.judgeNewsRelevance();
        scheduler.judgeNewsRelevance();

        verify(relevance, timeout(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)).times(2)).judgeRecent();
    }
}
