package ssafy.a507.backend.domain.research.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.common.ai.GpuAiClient;
import ssafy.a507.backend.domain.research.entity.NewsSignal;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.NewsSignalRepository;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * 뉴스 관련도 판정 (ANT-RESEARCH-02).
 *
 * <p>기사 한 건에 한 콜이다. 스무 건을 한 콜에 묶으면 콜 수가 1/20 이 되지만 한 건이 깨질 때
 * 스무 건을 잃는다 — 2026-09-09 에 없앤 종목 단위 배치와 같은 실수다. 자체 서빙이라 콜 수에
 * 값이 붙지 않으므로 묶지 않는다.
 *
 * <p>한 회차를 한 트랜잭션으로 묶지 않는다. 수집 배치와 같은 판단이다 — 기사 하나의 실패가
 * 나머지를 끌고 내려가면 안 되고, 다음 회차가 판정 없는 기사를 다시 집는다.
 *
 * <p>병렬로 부르지 않는다. 1,000건이 건당 2초여도 30분 남짓이고 밤 배치라 기다리는 사람이 없다.
 * ponytail: 단일 스레드 순차. 회차가 길어져 다음 배치와 겹치면 그때 고정 크기 풀을 넣는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsRelevanceService {

    /** 한 회차에 판정할 최대 건수. 수집이 하루 1,000건 안팎이라 밀린 이틀치까지 덮는다. */
    private static final int MAX_PER_RUN = 3_000;

    /** 이 구간보다 오래된 기사는 판정하지 않는다. 재료로 쓰이는 창(뉴스 수집 되돌아보기)보다 넓다. */
    private static final Duration LOOKBACK = Duration.ofDays(21);

    static final String INSTRUCTION =
            """
            너는 기업 뉴스 선별기다. 아래 기사가 그 회사의 사업·실적·공시·주가에 관한 것인지 판정한다.

            - 회사와 이름만 같은 스포츠 구단·지역·인물·상표의 소식은 "무관" 이다.
              예: "두산 선발 최승용 3이닝 4실점"(KBO 두산 베어스), "한화 9회말 끝내기"(한화 이글스).
            - 회사가 주체이거나 대상인 사업 소식이면 "관련" 이다. 스포츠가 소재여도 회사의 사업
              결정이면 "관련" 이다. 예: 구단 매각·인수, 중계권 계약, 스폰서십 체결.
            - 판단이 서지 않으면 "관련" 으로 둔다. 재료에서 빼는 쪽이 더 위험하다.
            - 다른 말을 쓰지 않는다. "관련" 또는 "무관" 한 낱말만 출력한다.
            """;

    private final GpuAiClient gpuAiClient;
    private final ResearchDocumentRepository researchDocumentRepository;
    private final NewsSignalRepository newsSignalRepository;

    /**
     * 판정이 없는 최근 기사를 판정한다.
     *
     * @return 새로 남긴 판정 수
     */
    public int judgeRecent() {
        if (!gpuAiClient.isConfigured()) {
            log.info("[SIGNAL] app.ai.gpu 설정이 없어 관련도 판정을 건너뛴다 — 재료 선정은 정규식으로 떨어진다");
            return 0;
        }

        String promptVersion = gpuAiClient.promptVersion();
        List<ResearchDocument> targets = researchDocumentRepository.findUnjudged(
                ResearchDocument.Source.NEWS,
                Instant.now().minus(LOOKBACK),
                promptVersion,
                Limit.of(MAX_PER_RUN));
        if (targets.isEmpty()) {
            log.info("[SIGNAL] 판정할 기사가 없다");
            return 0;
        }

        int judged = 0;
        for (ResearchDocument document : targets) {
            try {
                Boolean relevant = relevant(document);
                if (relevant == null) {
                    // 판정을 읽지 못한 건은 남기지 않는다 — 다음 회차가 다시 집는다.
                    continue;
                }
                save(document, relevant, promptVersion);
                judged++;
            } catch (AiException e) {
                log.warn("[SIGNAL] 판정 실패 document={} — {}", document.getId(), e.getMessage());
            } catch (DataAccessException e) {
                log.warn("[SIGNAL] 저장 실패 document={} — {}", document.getId(), e.getMessage());
            }
        }
        log.info("[SIGNAL] 관련도 판정 {}건 (대상 {}건)", judged, targets.size());
        return judged;
    }

    private Boolean relevant(ResearchDocument document) {
        String company = document.getStock() == null ? "(시장 전체)" : document.getStock().getName();
        String input = "회사: " + company + "\n기사: " + document.excerpt();
        return parse(gpuAiClient.complete(INSTRUCTION, input));
    }

    private void save(ResearchDocument document, boolean relevant, String promptVersion) {
        String model = gpuAiClient.model();
        // 다시 저장하는 것이 요점이다. 배치에는 트랜잭션이 없어 조회한 엔티티가 곧 분리된다 —
        // 값만 바꾸면 더티 체킹이 없어 변경이 사라진다(세대를 올려도 옛 판정이 그대로 남는다).
        NewsSignal signal = newsSignalRepository
                .findByDocument_Id(document.getId())
                .map(s -> {
                    s.rejudge(relevant, model, promptVersion);
                    return s;
                })
                .orElseGet(() -> NewsSignal.judged(document, relevant, model, promptVersion));
        newsSignalRepository.save(signal);
    }

    /**
     * 응답에서 판정을 읽는다. 읽지 못하면 null 이다 — 그 건은 남기지 않는다.
     *
     * <p>"무관"을 먼저 본다. "관련 없음"·"관련이 없다" 처럼 부정형에도 "관련"이 들어 있어,
     * "관련"부터 찾으면 정반대로 읽는다.
     */
    static Boolean parse(String text) {
        if (text == null) {
            return null;
        }
        String answer = text.strip();
        if (answer.contains("무관") || answer.contains("관련 없") || answer.contains("관련없")) {
            return false;
        }
        if (answer.contains("관련")) {
            return true;
        }
        return null;
    }
}
