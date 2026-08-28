package ssafy.a507.backend.domain.season;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시즌 뉴스와 AI 힌트. 진행일을 넘어선 게임일 행은 응답에 실으면 안 된다.
 * AI_HINT는 시즌 생성 시 게임일별로 미리 만들어 둔다(실시간 생성은 화면이 멈춘다).
 */
@Entity
@Table(name = "season_news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonNews {

    /** AI_HINT만 생성물이고 나머지는 당시 실제 정보다. */
    public enum Kind {
        NEWS,
        DISCLOSURE,
        IR,
        EVENT,
        AI_HINT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    /** NULL이면 시장 전체 뉴스다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker_id")
    private SeasonTicker ticker;

    @Column(name = "game_day", nullable = false)
    private int gameDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Kind kind;

    @Column(nullable = false, length = 200)
    private String title;

    /**
     * 종목명·연도를 치환한 본문(블라인드 누수 차단).
     * AI_HINT는 그 게임일 사건 요약과 생각해볼 질문만 담고 확률 수치는 넣지 않는다.
     */
    @Column(columnDefinition = "text")
    private String body;

    /** AI_HINT 생성에 쓴 프롬프트·모델 버전. 나머지 kind는 NULL이다. */
    @Column(name = "prompt_version", length = 16)
    private String promptVersion;
}
