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

/** 리서치 포인트. 예측 등록 시 "내 근거로 선택"의 대상이 된다. */
@Entity
@Table(name = "research_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchPoint {

    /** 화면 3열의 축. */
    public enum Kind {
        POSITIVE,
        RISK,
        CHECK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_code", nullable = false)
    private Stock stock;

    /** 어느 영업일 기준으로 뽑은 포인트인지. */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    @Column(nullable = false, length = 300)
    private String body;

    /** "근거 보기" 원문. 출처 태그는 여기서 파생하며 NULL이면 종합 포인트다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private ResearchDocument document;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 생성 (ANT-RESEARCH-04, 배치 B6).
     *
     * <p>세대 태그를 두지 않는다. 브리핑은 프롬프트를 고치면 같은 행을 다시 썼지만, 포인트는
     * 종목·날짜마다 여러 행이라 재생성이 곧 삭제 후 삽입이 된다. 그런데 사용자가 고른 포인트는
     * {@code prediction_evidences} 가 FK 로 물고 있어, 배치가 지우면 남의 예측 근거가 사라진다.
     * 포인트는 그날의 스냅샷으로 굳히고, 프롬프트 변경은 다음 영업일부터 반영한다.
     *
     * @param document 근거가 된 원문 · null 이면 여러 재료를 묶은 종합 포인트다
     */
    public static ResearchPoint of(
            Stock stock, LocalDate targetDate, Kind kind, String body, ResearchDocument document) {
        ResearchPoint point = new ResearchPoint();
        point.stock = stock;
        point.targetDate = targetDate;
        point.kind = kind;
        point.body = body;
        point.document = document;
        return point;
    }
}
