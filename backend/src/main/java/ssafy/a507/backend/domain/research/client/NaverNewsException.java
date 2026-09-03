package ssafy.a507.backend.domain.research.client;

/** 뉴스 검색 호출·해석 실패. 종목 하나만 건너뛰고 회차를 이어 가도록 구분해 던진다. */
public class NaverNewsException extends RuntimeException {

    public NaverNewsException(String message, Throwable cause) {
        super(message, cause);
    }
}
