package ssafy.a507.backend.domain.research.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.domain.research.entity.NewsSignal;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.NewsSignalRepository;

/**
 * 무관 기사를 걷어 내는 규칙 한 곳 (ANT-RESEARCH-02).
 *
 * <p>규칙은 한 줄이다 — <b>판정이 있으면 그것을 따르고, 없으면 스포츠 어휘 정규식으로 떨어진다.</b>
 * 판정은 {@link NewsRelevanceService} 가 밤 배치로 기사마다 남긴다. 판정이 없는 때는 배치가 아직
 * 안 돌았거나 {@code app.ai.gpu} 설정이 없는 환경(로컬·CI)이다.
 *
 * <p>투자 포인트 재료와 종목 화면의 문서 목록이 <b>같은 규칙</b>을 써야 해서 여기 모았다. 규칙이
 * 두 곳에 따로 있으면 언젠가 갈리고, 그러면 "포인트에는 안 쓰인 기사가 목록에는 뜨는" 상태가 된다.
 *
 * <p>공시(DART)는 거르지 않는다. 회사가 스스로 낸 것이라 무관할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class NewsRelevanceFilter {

    private final NewsSignalRepository newsSignalRepository;

    /** 무관 기사를 뺀 목록. 들어온 순서를 지킨다. */
    public List<ResearchDocument> keepRelevant(List<ResearchDocument> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, Boolean> signals = signals(documents);
        return documents.stream().filter(d -> isRelevant(d, signals)).toList();
    }

    /** 후보 문서들의 판정을 한 번에 읽는다 — 건별 조회는 왕복이 너무 잦다. */
    private Map<Long, Boolean> signals(List<ResearchDocument> documents) {
        return newsSignalRepository
                .findByDocument_IdIn(documents.stream().map(ResearchDocument::getId).toList())
                .stream()
                .collect(Collectors.toMap(s -> s.getDocument().getId(), NewsSignal::isRelevant));
    }

    private static boolean isRelevant(ResearchDocument document, Map<Long, Boolean> signals) {
        if (document.getSource() == ResearchDocument.Source.DART) {
            return true;
        }
        Boolean judged = signals.get(document.getId());
        return judged != null
                ? judged
                : !NewsIngestService.isNoise(document.getTitle(), document.getSnippet());
    }
}
