package ssafy.a507.backend.common.ai;

/** GMS 호출·응답 해석 실패. 호출부가 대상 하나만 건너뛰고 회차를 이어 가도록 구분해 던진다. */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
