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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * AI 브리핑. 수치는 결정론적 코드가 계산하고 여기에는 서술 문단만 담는다.
 * 종목 추천·미래 예측 문구는 넣지 않는다.
 */
@Entity
@Table(
        name = "ai_briefings",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_ai_briefings_target",
                        columnNames = {"scope", "stock_code", "target_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiBriefing {

    public enum Scope {
        MARKET,
        STOCK
    }

    /** {@code headline} 컬럼 폭. */
    public static final int MAX_HEADLINE_LENGTH = 200;

    /** ERD 가 정한 본문 상한(≤5000자). 모델이 길이를 넘기면 잘라 저장한다. */
    public static final int MAX_BODY_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Scope scope;

    /** STOCK일 때만 채운다. MARKET이면 NULL이어야 한다(CHECK 제약은 DDL에서). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code")
    private Stock stock;

    /** 어느 영업일 기준 브리핑인가. 생성 시각과 분리한다. */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false, length = 200)
    private String headline;

    @Column(columnDefinition = "text")
    private String body;

    /** 섞인 세대를 구분하는 유일한 값. */
    @Column(name = "prompt_version", length = 16)
    private String promptVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 배치 B6 의 생성 (ANT-RESEARCH-03).
     *
     * <p>UQ 는 {@code (scope, stock_code, target_date)} 지만 MARKET 행은 {@code stock_code}
     * 가 NULL 이라 Postgres 유니크가 겹침을 잡지 못한다 — MARKET 의 멱등은 저장 전에 같은
     * 날짜 행을 찾아 {@link #rewrite} 하는 서비스 쪽 책임이고, UQ 는 STOCK 의 안전판이다.
     *
     * @param stock STOCK 이면 필수 · MARKET 이면 null
     */
    public static AiBriefing of(
            Scope scope,
            Stock stock,
            LocalDate targetDate,
            String headline,
            String body,
            String promptVersion) {
        AiBriefing briefing = new AiBriefing();
        briefing.scope = scope;
        briefing.stock = stock;
        briefing.targetDate = targetDate;
        briefing.rewrite(headline, body, promptVersion);
        return briefing;
    }

    /**
     * 같은 대상·날짜의 재생성. 프롬프트 세대를 올렸을 때 옛 행을 지우고 새로 넣는 대신 제자리에서
     * 바꾼다 — id 가 유지되어 이미 열어 둔 상세 링크가 깨지지 않는다.
     */
    public void rewrite(String headline, String body, String promptVersion) {
        this.headline = CorpProfile.cut(headline, MAX_HEADLINE_LENGTH);
        this.body = CorpProfile.cut(body, MAX_BODY_LENGTH);
        this.promptVersion = promptVersion;
    }
}
