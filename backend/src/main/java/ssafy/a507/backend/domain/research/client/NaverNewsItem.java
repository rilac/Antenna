package ssafy.a507.backend.domain.research.client;

import java.time.Instant;

/**
 * 네이버 뉴스 검색 결과 한 건.
 *
 * <p>본문이 없다 — API 가 주지 않는다. {@code description} 은 검색어 주변을 잘라 낸 발췌이고,
 * 우리도 원문 본문은 저장하지 않는다(저작권). 요약 배치는 제목과 이 발췌만으로 카드를 만든다.
 *
 * @param title 태그·엔티티를 걷어 낸 제목
 * @param originUrl 언론사 원문 주소 · 멱등 키의 재료다
 * @param snippet 태그·엔티티를 걷어 낸 발췌
 * @param publishedAt 발행 시각
 */
public record NaverNewsItem(String title, String originUrl, String snippet, Instant publishedAt) {}
