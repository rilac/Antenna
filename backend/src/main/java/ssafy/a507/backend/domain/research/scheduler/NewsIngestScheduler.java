package ssafy.a507.backend.domain.research.scheduler;

import lombok.RequiredArgsConstructor;
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
@Component
@RequiredArgsConstructor
public class NewsIngestScheduler {

    private final NewsIngestService newsIngestService;
    private final BriefingGenerationService briefingGenerationService;
    private final NewsRelevanceService newsRelevanceService;

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
        newsRelevanceService.judgeRecent();
    }
}
