package ssafy.a507.backend.domain.research.scheduler;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.research.service.BriefingGenerationService;
import ssafy.a507.backend.domain.research.service.NewsIngestService;
import ssafy.a507.backend.domain.research.service.NewsRelevanceService;

/**
 * 뉴스 수집과 시장 브리핑 생성(배치 B6)의 시각을 정한다 (ANT-RESEARCH-02/03).
 *
 * <p>둘을 한 메서드로 잇지 않고 cron 을 나눈 이유 — 수집은 네이버 한도, 생성은 GMS 과금에
 * 걸린다. 한쪽 자격증명만 있는 환경(키를 아직 못 받은 팀원, 생성을 끈 배포)에서 다른 쪽까지
 * 멈추면 안 된다. 각자 자기 키가 없으면 자기 회차만 건너뛴다.
 *
 * <p><b>종목 단위 AI 생성은 여기 없다(2026-09-09).</b> 종목 브리핑은 없앴고, 투자 포인트는 사용자가
 * 예측 탭에서 요청한 종목만 그 자리에서 만든다({@code ResearchPointService}). 300종목을 매일 미리
 * 만들던 구조가 GMS 토큰을 하루 만에 태웠다. 배치가 부르는 LLM 은 시장 브리핑 하루 1콜뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsIngestScheduler {

    private final NewsIngestService newsIngestService;
    private final BriefingGenerationService briefingGenerationService;
    private final NewsRelevanceService newsRelevanceService;

    static final String RELEVANCE_THREAD = "relevance-1";

    /**
     * 관련도 판정 전용 스레드.
     *
     * <p>앱의 {@code @Scheduled} 는 스레드 하나를 나눠 쓴다 — 스케줄러 풀 크기를 따로 정하지 않아
     * 기본값 1 이다. 판정 한 회차는 기사 3,000건 × 0.8초로 첫날 밤 40분쯤 걸리는데, 그동안 스케줄러
     * 스레드를 쥐고 있으면 5초마다 도는 체인 인덱서가 통째로 멈춘다. 그래서 판정만 여기로 넘긴다.
     *
     * <p><b>풀 크기를 늘리지 않은 이유.</b> 13:00 시세 수집 → 13:30 예측 판정 → 13:35 랭킹이 지금은
     * 스레드가 하나라 저절로 줄을 선다. 풀을 늘리면 수집이 길어진 날 판정이 어제 시세로 먼저 돈다.
     * 오래 걸리는 것 하나만 떼어 내면 나머지의 순서는 그대로다.
     *
     * <p>수집(20:00) → 판정(21:00) 순서도 지켜진다. 21:00 트리거 자체는 여전히 스케줄러 스레드에서
     * 돌기 때문에, 수집이 21시를 넘기면 트리거가 수집이 끝날 때까지 기다렸다가 판정을 넘긴다.
     *
     * <p>데몬 스레드다 — 재배포로 앱이 내려갈 때 돌던 회차가 JVM 종료를 붙잡지 않는다. 끊긴 회차는
     * 판정 없는 기사가 그대로 남아 다음 회차가 이어 집는다.
     */
    private final ExecutorService relevanceExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, RELEVANCE_THREAD);
        thread.setDaemon(true);
        return thread;
    });

    /** 뉴스 수집 — 매일 20:00. 장 마감 뒤 마감 기사까지 담기도록 저녁에 돈다. */
    @Scheduled(cron = "${app.naver-news.cron:0 0 20 * * *}", zone = "Asia/Seoul")
    public void ingestNews() {
        newsIngestService.ingestNews();
    }

    /**
     * 배치 B6 — 매일 20:30, 시장 브리핑 1건. 수집 30분 뒤라 그날 시세·지수가 재료가 된다.
     *
     * <p>{@code app.ai.generation-cron} 이 {@code "-"}(Spring 의 cron 비활성 표시)면 등록만 되고 돌지
     * 않는다 — 2026-09-09 과금 사고 뒤 껐다. 지금은 하루 1콜이라 켜도 된다. application.yaml 참고.
     */
    // "${...:-}" 는 빈 값이 아니라 기본값이 "-" 라는 뜻이다(구분자는 첫 ":", 그 뒤가 통째로
    // 기본값). 속성이 없는 환경에서도 켜지지 않도록 어노테이션 기본값까지 막았다.
    @Scheduled(cron = "${app.ai.generation-cron:-}", zone = "Asia/Seoul")
    public void generate() {
        briefingGenerationService.generate();
    }

    /**
     * 뉴스 관련도 판정 — 매일 21:00, 수집 한 시간 뒤. 자체 서빙(GPU) 으로만 돈다.
     *
     * <p>수집과 나눈 이유는 위와 같다 — 판정이 안 되는 환경에서도 수집은 그대로 돌아야 한다.
     * 기사당 한 콜이라 하루 1,000콜을 넘겨 상용 무료 티어로는 못 돌린다. {@code app.ai.gpu} 설정이
     * 비어 있으면 회차가 로그 한 줄만 남기고 건너뛰고, 재료 선정은 정규식으로 떨어진다.
     */
    @Scheduled(cron = "${app.ai.gpu.relevance-cron:-}", zone = "Asia/Seoul")
    public void judgeNewsRelevance() {
        relevanceExecutor.execute(this::runRelevance);
    }

    /**
     * 전용 스레드에서 도는 한 회차. 예외를 여기서 받아 로그로 남긴다 — 스케줄러가 받아 적어 주던
     * 것을 이제 아무도 받지 않으므로, 여기서 삼키지 않으면 표준 오류로만 흘러 로그 검색에 안 걸린다.
     */
    private void runRelevance() {
        try {
            newsRelevanceService.judgeRecent();
        } catch (RuntimeException e) {
            log.warn("[SIGNAL] 관련도 판정 회차 실패 — {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    void shutdownRelevance() {
        relevanceExecutor.shutdownNow();
    }
}
