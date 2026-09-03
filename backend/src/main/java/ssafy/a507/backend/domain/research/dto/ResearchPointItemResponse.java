package ssafy.a507.backend.domain.research.dto;

import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;

/**
 * 포인트 카드 한 장.
 *
 * <p>{@code documentId} 가 "근거 보기" 링크의 대상이고 {@code source} 는 그 문서에서 파생한 출처
 * 태그다(NEWS · DART · IR). 둘 다 null 일 수 있다 — 시세·재무 수치에서 나온 종합 포인트다.
 *
 * <p>{@code id} 는 화면이 그대로 들고 있다가 예측 등록 본문의 {@code evidencePointIds} 에 실어
 * 보낸다("내 근거로 선택"에는 별도 API 가 없다 — 명세 -215).
 */
public record ResearchPointItemResponse(
        Long id, String body, Long documentId, ResearchDocument.Source source) {

    public static ResearchPointItemResponse of(ResearchPoint point) {
        ResearchDocument document = point.getDocument();
        return new ResearchPointItemResponse(
                point.getId(),
                point.getBody(),
                document == null ? null : document.getId(),
                document == null ? null : document.getSource());
    }
}
