package ssafy.a507.backend.domain.research.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기사 한 건의 관련도 판정 (ANT-RESEARCH-02).
 *
 * <p>회사의 사업·실적·공시·주가 이야기인가만 본다. 이름만 같은 스포츠 구단·지역·인물 소식을
 * 투자 포인트 재료에서 빼려고 만들었다 — "두산 선발 3이닝 4실점" 이 두산 위험 요인으로 올라간 적이
 * 있다. 어휘 정규식으로는 갈라지지 않는다: 같은 단어가 스포츠 기사에도 사업 기사에도 나온다
 * ("엔씨소프트, NC다이노스 매각 검토").
 *
 * <p><b>점수가 아니라 이진이다.</b> 임계값을 실측으로 정해야 하는 부담이 없고, 재료 정렬은 지금도
 * 날짜순이라 점수가 필요하지 않다. 정렬에 쓸 일이 생기면 그때 열을 늘린다.
 *
 * <p>{@code prompt_version} 을 함께 남긴다. 지시문을 고치면 옛 세대 행만 골라 다시 판정한다.
 */
@Entity
@Table(
        name = "news_signals",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_news_signals_document",
                        columnNames = {"document_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private ResearchDocument document;

    /** 회사의 사업·실적·공시·주가에 관한 기사인가. */
    @Column(nullable = false)
    private boolean relevant;

    /** 판정한 모델 이름. 모델을 바꾼 뒤 판정 품질을 견줄 때 쓴다. */
    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "judged_at", nullable = false)
    private Instant judgedAt;

    public static NewsSignal judged(
            ResearchDocument document, boolean relevant, String model, String promptVersion) {
        NewsSignal signal = new NewsSignal();
        signal.document = document;
        signal.relevant = relevant;
        signal.model = model;
        signal.promptVersion = promptVersion;
        signal.judgedAt = Instant.now();
        return signal;
    }

    /** 같은 문서를 다시 판정했을 때. 행을 늘리지 않고 덮는다 — 최신 판정만 의미가 있다. */
    public void rejudge(boolean relevant, String model, String promptVersion) {
        this.relevant = relevant;
        this.model = model;
        this.promptVersion = promptVersion;
        this.judgedAt = Instant.now();
    }
}
