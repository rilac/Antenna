package ssafy.a507.backend.domain.research.dto;

import java.time.Instant;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;

/**
 * 뉴스·공시 카드 한 장.
 *
 * <p>{@code summary} 가 null 일 수 있다 — 수집은 됐지만 아직 요약 배치가 돌지 않은 건이다.
 * 화면은 그때 제목만 보여 준다. 요약을 기다리느라 목록에서 감추면, 방금 난 기사가 가장
 * 늦게 뜨는 셈이 된다.
 *
 * <p>발췌({@code snippet})는 내려보내지 않는다. 화면에 필요한 것은 요약이고, 발췌는 요약을
 * 다시 만들기 위한 서버 쪽 재료다.
 */
public record ResearchDocumentItemResponse(
        Long id,
        ResearchDocument.Source source,
        String title,
        String summary,
        String originUrl,
        Instant publishedAt) {

    public static ResearchDocumentItemResponse of(ResearchDocument document) {
        return new ResearchDocumentItemResponse(
                document.getId(),
                document.getSource(),
                document.getTitle(),
                document.getSummary(),
                document.getOriginUrl(),
                document.getPublishedAt());
    }
}
