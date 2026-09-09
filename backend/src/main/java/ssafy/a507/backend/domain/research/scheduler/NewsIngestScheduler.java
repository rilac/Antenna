package ssafy.a507.backend.domain.research.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.research.service.BriefingGenerationService;
import ssafy.a507.backend.domain.research.service.DocumentSummaryService;
import ssafy.a507.backend.domain.research.service.NewsIngestService;
import ssafy.a507.backend.domain.research.service.ResearchPointGenerationService;

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
    private final ResearchPointGenerationService researchPointGenerationService;

    /** 뉴스 수집 — 매일 20:00. 장 마감 뒤 마감 기사까지 담기도록 저녁에 돈다. */
    @Scheduled(cron = "${app.naver-news.cron:0 0 20 * * *}", zone = "Asia/Seoul")
    public void ingestNews() {
        newsIngestService.ingestNews();
    }

    /**
     * 배치 B6 — 매일 20:30. 뉴스 요약 → AI 브리핑(ANT-RESEARCH-03) → 투자 포인트(-04) 순으로 잇는다.
     *
     * <p><b>★ 2026-09-09 현재 이 회차는 꺼져 있다 (김성령/KSR).</b> {@code app.ai.news-summary-cron}
     * 이 {@code "-"}(Spring 의 cron 비활성 표시)라 아래 코드는 호출되지 않는다. GMS 과금 때문이다 —
     * 한 회차가 요약 최대 1000콜 + 브리핑(1 + 종목 수 ≈300) + 포인트(종목 수 ≈300)로 하루 1500콜
     * 근처를 쓰고, 종목이 늘면 상한 없이 같이 는다.
     *
     * <p><b>되살리기 전에 모델을 다시 고른다.</b> cron 만 되돌리면 같은 청구서가 그대로 돌아온다.
     * 모델({@code app.ai.model})·회차당 호출 수(요약은 {@code DocumentSummaryService.MAX_PER_RUN},
     * 브리핑·포인트는 대상 종목 범위) 중 무엇을 줄일지 정하는 게 먼저다. 재개 절차와 밀린 요약
     * 대기 물량 주의사항은 {@code application.yaml} 의 KILL SWITCH 주석에 적어 뒀다.
     *
     * <p>아래는 배치가 살아 있을 때의 설계 근거다 — 끄면서 지우지 않았다. 재개할 때 그대로 쓴다.
     *
     * <p>한 메서드로 묶은 이유 — 브리핑이 그날 요약을 재료로 쓰므로 순서가 구조로 보장돼야
     * 한다. cron 두 개로 시각을 벌려 두면 스레드 풀 크기(지금 1)에 기대는 암묵적 순서가 되고,
     * 요약 시간이 늘 때마다 뒤 cron 을 다시 맞춰야 한다. 셋 다 같은 GMS 키라 "한쪽 키만 있는
     * 환경" 논리도 여기엔 없다. 요약이 통째로 죽어도(런타임 예외) 브리핑은 돈다 — 재료가 하루
     * 오래된 것뿐이다.
     *
     * <p>포인트가 맨 뒤인 이유는 뉴스 요약을 근거 문서로 지목하기 때문이다. 브리핑과는 서로
     * 참조하지 않지만, 실패했을 때 더 중요한 쪽(브리핑)이 먼저 한도를 쓰게 둔다.
     *
     * <p>세 단계를 각각 감싸는 이유 — 생성 서비스 안의 예외 격리는 종목 루프만 덮고, 그 앞의
     * 기준일 조회·대상 종목 선정은 밖에 있다. 거기서 한 번 터지면 뒤 단계가 통째로 밀리는데,
     * 포인트는 기준일 하루만 노리고 백필이 없어 그날 종목은 영영 포인트가 없다.
     */
    // "${...:-}" 는 빈 값이 아니라 기본값이 "-" 라는 뜻이다(구분자는 첫 ":", 그 뒤가 통째로
    // 기본값). 속성이 없는 환경에서도 켜지지 않도록 어노테이션 기본값까지 막았다. 원래 기본값은
    // "0 30 20 * * *" 였다 — 이 자리에 살아 있는 cron 을 남겨 두면 설정 한 줄이 빠진 환경이
    // 조용히 과금을 다시 시작한다.
    @Scheduled(cron = "${app.ai.news-summary-cron:-}", zone = "Asia/Seoul")
    public void summarizeNews() {
        try {
            documentSummaryService.summarizeNews();
        } catch (RuntimeException e) {
            log.warn("[B6] 뉴스 요약 회차 실패 — 브리핑은 이어 간다: {}", e.getMessage());
        }
        try {
            briefingGenerationService.generate();
        } catch (RuntimeException e) {
            log.warn("[B6] 브리핑 회차 실패 — 포인트는 이어 간다: {}", e.getMessage());
        }
        researchPointGenerationService.generate();
    }
}
