package ssafy.a507.backend.domain.research.dto;

import java.time.Instant;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;

/**
 * 뉴스·공시 카드 한 장.
 *
 * <p>{@code snippet} 은 네이버 검색 API 가 준 발췌 그대로다. 공시는 발췌가 없어 null — 보고서명이 곧
 * 내용이다. AI 요약을 따로 만들지 않는다(2026-09-09 폐지) — 입력이 이 발췌뿐이라 발췌를 다시 쓴
 * 문장만 나왔다.
 */
public record ResearchDocumentItemResponse(
        Long id,
        ResearchDocument.Source source,
        String title,
        String snippet,
        String originUrl,
        Instant publishedAt) {

    public static ResearchDocumentItemResponse of(ResearchDocument document) {
        return new ResearchDocumentItemResponse(
                document.getId(),
                document.getSource(),
                document.getTitle(),
                document.getSnippet(),
                document.getOriginUrl(),
                document.getPublishedAt());
    }
}
