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
@Table(name = "ai_briefings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiBriefing {

    public enum Scope {
        MARKET,
        STOCK
    }

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
}
