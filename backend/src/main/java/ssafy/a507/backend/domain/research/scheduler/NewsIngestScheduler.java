package ssafy.a507.backend.domain.research.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.research.service.BriefingGenerationService;
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
    private final BriefingGenerationService briefingGenerationService;

    /** 뉴스 수집 — 매일 20:00. 장 마감 뒤 마감 기사까지 담기도록 저녁에 돈다. */
    @Scheduled(cron = "${app.naver-news.cron:0 0 20 * * *}", zone = "Asia/Seoul")
    public void ingestNews() {
        newsIngestService.ingestNews();
    }

    /**
     * 배치 B6 — 매일 20:30. 뉴스 요약 뒤에 AI 브리핑(ANT-RESEARCH-03)을 이어 돈다.
     *
     * <p>둘을 한 메서드로 묶은 이유 — 브리핑이 그날 요약을 재료로 쓰므로 순서가 구조로 보장돼야
     * 한다. cron 두 개로 시각을 벌려 두면 스레드 풀 크기(지금 1)에 기대는 암묵적 순서가 되고,
     * 요약 시간이 늘 때마다 뒤 cron 을 다시 맞춰야 한다. 둘 다 같은 GMS 키라 "한쪽 키만 있는
     * 환경" 논리도 여기엔 없다. 요약이 통째로 죽어도(런타임 예외) 브리핑은 돈다 — 재료가 하루
     * 오래된 것뿐이다.
     */
    @Scheduled(cron = "${app.ai.news-summary-cron:0 30 20 * * *}", zone = "Asia/Seoul")
    public void summarizeNews() {
        try {
            documentSummaryService.summarizeNews();
        } catch (RuntimeException e) {
            log.warn("[B6] 뉴스 요약 회차 실패 — 브리핑은 이어 간다: {}", e.getMessage());
        }
        briefingGenerationService.generate();
    }
}
