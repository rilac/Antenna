package ssafy.a507.backend.common.ai;

/**
 * GMS 가 키를 거부했다(401) — 토큰 소진("This GMS key has no token left")이거나 잘못된 키다.
 *
 * <p>{@link AiException} 과 달리 <b>대상 하나를 건너뛸 일이 아니라 회차를 접을 일</b>이다. 같은 키로
 * 다음 종목을 불러도 같은 답이 온다 — 2026-09-08 실제로 코인이 바닥난 뒤 남은 종목 300개를
 * 0.7초 만에 전부 두드렸다. 배치 루프는 이 예외를 먼저 잡아 그 회차를 끝낸다.
 */
public class AiKeyRejectedException extends AiException {

    public AiKeyRejectedException(String message) {
        super(message);
    }

    public AiKeyRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
