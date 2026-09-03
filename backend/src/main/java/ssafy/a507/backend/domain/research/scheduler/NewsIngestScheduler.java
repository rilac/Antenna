package ssafy.a507.backend.domain.research.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.research.service.DocumentSummaryService;
import ssafy.a507.backend.domain.research.service.NewsIngestService;

/**
 * 뉴스 수집과 요약 생성의 시각을 정한다 (ANT-RESEARCH-02).
 *
 * <p>둘을 한 메서드로 잇지 않고 cron 을 나눈 이유 — 수집은 네이버 한도, 요약은 GMS 과금에
 * 걸린다. 한쪽 자격증명만 있는 환경(키를 아직 못 받은 팀원, 요약을 끈 배포)에서 다른 쪽까지
 * 멈추면 안 된다. 각자 자기 키가 없으면 자기 회차만 건너뛴다.
 *
 * <p>수집이 먼저, 요약이 30분 뒤다. 같은 시각에 두면 그날 들어온 기사가 다음 날에야 요약된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsIngestScheduler {

    private final NewsIngestService newsIngestService;
    private final DocumentSummaryService documentSummaryService;

    /** 뉴스 수집 — 매일 20:00. 장 마감 뒤 마감 기사까지 담기도록 저녁에 돈다. */
    @Scheduled(cron = "${app.naver-news.cron:0 0 20 * * *}", zone = "Asia/Seoul")
    public void ingestNews() {
        newsIngestService.ingestNews();
    }

    /** 요약 생성(B6) — 매일 20:30. 방금 들어온 기사와, 세대가 뒤처진 옛 기사를 함께 집는다. */
    @Scheduled(cron = "${app.ai.news-summary-cron:0 30 20 * * *}", zone = "Asia/Seoul")
    public void summarizeNews() {
        documentSummaryService.summarizeNews();
    }
}
