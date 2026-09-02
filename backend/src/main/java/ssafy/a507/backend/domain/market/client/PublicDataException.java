package ssafy.a507.backend.domain.market.client;

/**
 * 포털 수집이 실패했다. 사용자 요청이 아니라 배치 안에서 터지는 것이라 API 오류 어휘
 * (BusinessException)를 쓰지 않는다 — 수집 회차를 FAILED 로 적고 다음 회차에 다시 시도한다.
 */
public class PublicDataException extends RuntimeException {

    public PublicDataException(String message) {
        super(message);
    }

    public PublicDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
