package ssafy.a507.backend.domain.research;

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
import ssafy.a507.backend.domain.market.Stock;

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
}
