package ssafy.a507.backend.domain.research.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

/**
 * 뉴스 검색 호출·해석 실패.
 *
 * <p>종목 하나의 문제인지 회차 전체의 문제인지를 구분해 던진다 — 자격증명이 틀리면(401·403)
 * 다음 종목도 똑같이 실패하고, 한도를 넘겼으면(429) 남은 호출이 전부 헛것이다. 그때는 종목
 * 하나를 건너뛰는 대신 회차를 접어야 한다. 그 밖의 실패(일시적 5xx·타임아웃)는 종목 단위다.
 */
public class NaverNewsException extends RuntimeException {

    private final boolean abortsRun;

    public NaverNewsException(String message, Throwable cause) {
        super(message, cause);
        this.abortsRun = cause instanceof RestClientResponseException e && abortsRun(e.getStatusCode().value());
    }

    private static boolean abortsRun(int status) {
        return status == HttpStatus.UNAUTHORIZED.value()
                || status == HttpStatus.FORBIDDEN.value()
                || status == HttpStatus.TOO_MANY_REQUESTS.value();
    }

    /** 이 실패가 남은 종목에도 똑같이 일어날 것이라 회차를 접어야 하는가. */
    public boolean abortsRun() {
        return abortsRun;
    }
}
