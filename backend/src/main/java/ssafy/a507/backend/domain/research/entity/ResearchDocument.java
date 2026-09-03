package ssafy.a507.backend.domain.research.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.a507.backend.domain.market.entity.Stock;

/** 뉴스·공시 원문 메타. 저작권 때문에 본문은 저장하지 않고 링크와 AI 요약만 둔다. */
@Entity
@Table(
        name = "research_documents",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_research_documents_source_external",
                        columnNames = {"source", "external_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchDocument {

    public enum Source {
        NEWS,
        DART,
        IR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL이면 시장 전체 뉴스다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code")
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Source source;

    /** 원천이 발급한 고유 ID(DART 접수번호 등). 재수집 멱등 키다. */
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "origin_url", nullable = false, length = 500)
    private String originUrl;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    /**
     * 원천이 함께 준 발췌. 뉴스 검색 응답의 {@code description} 이 여기 들어간다.
     *
     * <p><b>원문 본문이 아니다.</b> API 가 공식으로 내려 주는 두어 줄짜리 발췌이고, 저장하는
     * 이유는 요약을 다시 만들 수 있어야 해서다 — 프롬프트를 고쳐 재생성할 때 재료가 없으면
     * 제목만 남는데, 뉴스 제목은 잘려서 오는 일이 흔해 그것만으로는 요약이 되지 않는다.
     * 검색 API 로는 특정 기사를 다시 지목해 받을 수 없으므로 수집 때 함께 남긴다.
     *
     * <p>DART 공시에는 없다 — 공시는 보고서명이 곧 요약이라 발췌라 할 것이 따로 없다.
     */
    @Column(columnDefinition = "text")
    private String snippet;

    /** AI 요약 카드 본문. NULL이면 아직 요약 전이다. */
    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "summarized_at")
    private Instant summarizedAt;

    /** 생성에 쓴 프롬프트·모델 버전 태그. 프롬프트를 고치면 옛 값 행만 골라 재생성한다. */
    @Column(name = "prompt_version", length = 16)
    private String promptVersion;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    /**
     * 수집 시점의 생성 (ANT-RESEARCH-01).
     *
     * <p>{@code summary} 는 비워 둔다 — 요약 생성은 배치 B6(ANT-RESEARCH-02)의 몫이고, 여기서는
     * 재료만 쌓는다.
     */
    public static ResearchDocument collected(
            Stock stock,
            Source source,
            String externalId,
            String title,
            String originUrl,
            String snippet,
            Instant publishedAt) {
        ResearchDocument document = new ResearchDocument();
        document.stock = stock;
        document.source = source;
        document.externalId = externalId;
        document.title = title;
        document.originUrl = originUrl;
        document.snippet = snippet;
        document.publishedAt = publishedAt;
        document.collectedAt = Instant.now();
        return document;
    }

    /**
     * 요약 반영 (ANT-RESEARCH-02, 배치 B6).
     *
     * <p>{@code promptVersion} 을 함께 남기는 것이 요점이다 — 프롬프트를 고친 뒤에는 옛
     * 세대 행만 골라 다시 생성해야 하고, 이 값이 없으면 전부 다시 돌리는 수밖에 없다.
     */
    public void summarize(String summary, String promptVersion) {
        this.summary = summary;
        this.promptVersion = promptVersion;
        this.summarizedAt = Instant.now();
    }
}
