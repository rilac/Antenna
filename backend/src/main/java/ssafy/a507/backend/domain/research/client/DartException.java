package ssafy.a507.backend.domain.research.client;

/**
 * DART 호출·응답 실패. 그 대상은 이번 회차에 수집하지 못한 것으로 남는다.
 *
 * <p>{@link #isRateLimited()} 만 성격이 다르다. 다른 실패는 그 종목만 건너뛰고 다음으로
 * 넘어가면 되지만, 일일 한도를 넘긴 뒤에는 <b>남은 종목을 아무리 불러도 전부 실패</b>한다 —
 * 수백 번 헛호출을 하고 로그만 더럽히므로 회차를 통째로 접어야 한다.
 */
public class DartException extends RuntimeException {

    private final boolean rateLimited;

    public DartException(String message) {
        this(message, false);
    }

    public DartException(String message, Throwable cause) {
        super(message, cause);
        this.rateLimited = false;
    }

    private DartException(String message, boolean rateLimited) {
        super(message);
        this.rateLimited = rateLimited;
    }

    public static DartException rateLimited() {
        return new DartException("DART 일일 요청 한도를 넘겼다 — 이 회차는 접는다", true);
    }

    public boolean isRateLimited() {
        return rateLimited;
    }
}
