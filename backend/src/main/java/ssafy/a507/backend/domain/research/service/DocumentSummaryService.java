package ssafy.a507.backend.domain.research.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.common.ai.AiProperties;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * 뉴스 요약 생성 — 배치 B6 의 뉴스 갈래 (ANT-RESEARCH-02).
 *
 * <p><b>사용자 요청 경로에서 LLM 을 부르지 않는다.</b> 요약은 여기서 미리 만들어 DB 에 넣고,
 * 조회 API 는 저장된 문장만 읽는다(API 명세 §5.5).
 *
 * <p><b>DART 공시는 요약하지 않는다.</b> 공시에는 발췌가 없고 보고서명이 곧 요약이라
 * ("주요사항보고서(유상증자결정)") 모델에 넣어도 제목을 바꿔 쓴 문장이 나올 뿐이다. 호출
 * 비용만 늘고 정보가 늘지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSummaryService {

    /**
     * 한 회차가 생성할 최대 건수.
     *
     * <p>크기의 근거 — 2026-09-03 실측으로 종목당 하루 ~3건이 필터를 통과하고(검색 20건 ×
     * 16%), 300종목이면 ~900건/일이다. 건당 2.1초라 1000건이면 35분이고, 스케줄러가 단일
     * 스레드지만 20:30 뒤에는 다른 배치가 없어 겹치지 않는다. 200 으로 두면 하루치의 1/4만
     * 요약되고, 대상이 전역 최신순이라 뉴스가 많은 대형주가 상한을 독식한다.
     *
     * <p>ponytail: 상한이 상수다. 한도를 넘겨 과금이 튀는 것을 막는 안전판이고, 남은 건은
     * 다음 회차가 집는다. 종목별로 공평하게 나눠야 할 만큼 쌓이면 그때 종목당 상한을 둔다.
     */
    private static final int MAX_PER_RUN = 1000;

    private static final String INSTRUCTION =
            """
            너는 한국 주식 리서치 화면에 들어갈 뉴스 요약 카드를 쓴다.
            아래 규칙을 반드시 지켜라.

            - 한국어 평서문 2~3문장으로만 쓴다. 목록·제목·머리말을 붙이지 않는다.
            - 주어진 제목과 발췌에 있는 내용만 쓴다. 없는 사실을 채워 넣지 않는다.
            - 제목을 그대로 옮기지 말고, 무슨 일이 있었는지 풀어 쓴다.
            - 매수·매도 권유, 목표주가, 주가 전망 단정, 확률 수치를 쓰지 않는다.
            - 발췌가 잘려 있어 내용을 알 수 없으면 알 수 있는 사실만 한 문장으로 쓴다.
            """;

    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final ResearchDocumentRepository researchDocumentRepository;

    /**
     * 요약이 없거나 프롬프트 세대가 뒤처진 뉴스를 골라 생성한다.
     *
     * @return 이번 회차에 요약이 채워진 건수
     */
    public int summarizeNews() {
        if (!aiProperties.isConfigured()) {
            log.info("[B6] AI_API_KEY 가 없어 뉴스 요약을 건너뛴다");
            return 0;
        }

        String promptVersion = aiProperties.promptVersion();
        // 이 구간보다 오래된 기사는 요약이 비어 있어도 버린다. 배치를 꺼 둔 동안 쌓인 대기
        // 물량이 재개일에 통째로 청구되던 것을 막는다 — summary 가 NULL 인 채로 남긴다.
        Instant since = Instant.now().minus(Duration.ofDays(aiProperties.summaryLookbackDays()));
        List<ResearchDocument> pending = researchDocumentRepository.findPendingSummary(
                ResearchDocument.Source.NEWS, promptVersion, since, Limit.of(MAX_PER_RUN));
        if (pending.isEmpty()) {
            log.info("[B6] 요약할 뉴스가 없다 (최근 {}일)", aiProperties.summaryLookbackDays());
            return 0;
        }

        int summarized = 0;
        for (ResearchDocument document : pending) {
            try {
                String summary = aiClient.complete(INSTRUCTION, input(document));
                document.summarize(summary, promptVersion);
                researchDocumentRepository.save(document);
                summarized++;
            } catch (AiException e) {
                // 건 하나의 실패가 회차를 끝내면 뒤에 남은 기사가 통째로 밀린다. 다음 회차가
                // 다시 집는다 — 대상 조건이 "요약이 없거나 세대가 뒤처진 행"이라 그대로 남는다.
                log.warn("[B6] 뉴스 요약 실패 id={} — {}", document.getId(), e.getMessage());
            } catch (DataAccessException e) {
                log.warn("[B6] 뉴스 요약 저장 실패 id={} — {}", document.getId(), e.getMessage());
            }
        }
        log.info("[B6] 뉴스 요약 — 대상 {}건 중 {}건 생성 (prompt {})",
                pending.size(), summarized, promptVersion);
        return summarized;
    }

    /**
     * 모델에 넣을 재료. 종목명을 함께 준다 — 제목만으로는 어느 회사 이야기인지 모호한 기사가
     * 많고("업계 1위, 신공장 착공"), 그러면 요약이 엉뚱한 주어를 만든다.
     */
    private static String input(ResearchDocument document) {
        String snippet = document.getSnippet();
        return """
                종목: %s
                제목: %s
                발췌: %s
                """
                .formatted(
                        document.getStock().getName(),
                        document.getTitle(),
                        snippet == null || snippet.isBlank() ? "(없음)" : snippet);
    }
}
