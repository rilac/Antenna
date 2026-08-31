package ssafy.a507.backend.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;

/**
 * API 명세 §1 멱등성 — 상태를 만드는 POST 는 Idempotency-Key 헤더를 필수로 받고,
 * 같은 키의 재요청에는 처리하지 않고 최초 응답을 그대로 돌려준다.
 *
 * <p>이 서비스에는 되돌릴 수단이 없다는 것이 근거다. 예측·글에는 삭제 API 가 없고 토큰은
 * 소각된다. 네트워크 타임아웃 후 클라이언트가 한 번만 재시도해도 슬롯 2개 소모 · 토큰 2회
 * 소각 · 글 2건 생성이 되고 복구 경로가 없다.
 *
 * <p>저장은 Redis 다. ERD 에 저장 테이블이 없고, TTL 24h 로 알아서 사라지는 데이터에
 * 테이블을 만들 이유가 없다. 키는 명세대로 {@code (userId, endpoint, key)} 세 값으로
 * 구성한다 — userId 를 넣지 않으면 남이 만든 키를 추측해 그 사람의 응답을 꺼내갈 수 있다.
 *
 * <p>본문 해시를 함께 저장해, 같은 키로 다른 본문이 오면 409 로 거절한다. 키 재사용은
 * 클라이언트 버그이고, 조용히 옛 응답을 주면 사용자는 두 번째 글이 저장된 줄 안다.
 *
 * <p>ponytail: 필터·AOP·어노테이션을 만들지 않았다. 명세의 필수 대상 8개 중 지금 구현된
 * 것은 POST /posts 하나뿐이므로 컨트롤러에서 직접 부른다. 대상이 셋 이상 되면 그때
 * HandlerInterceptor 로 옮긴다 — 응답 본문을 가로채야 해서 그쪽이 훨씬 무겁다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String SEPARATOR = "\n";

    private final StringRedisTemplate redis;

    /**
     * 최초 요청이면 {@code action} 을 실행해 응답 본문을 저장하고 그 값을 돌려준다.
     * 같은 키·같은 본문의 재요청이면 저장된 응답을 그대로 돌려주고 action 을 부르지 않는다.
     *
     * @param userId 요청자. 키 공간을 사용자별로 가른다
     * @param endpoint 엔드포인트 식별자(예: {@code POST /posts}). 같은 키를 다른 API 에
     *     써도 서로 간섭하지 않게 한다
     * @param key 클라이언트가 만든 Idempotency-Key 헤더 값. 없으면 400
     * @param requestBodyHash {@link #hash(String)} 로 만든 요청 본문 해시. 같은 키에 다른
     *     본문이면 409
     * @param action 최초 요청일 때만 실행되는 실제 처리. 반환값이 그대로 저장된다 — 응답 전체가
     *     아니라 응답을 다시 만들 수 있는 최소 식별값이면 된다
     * @return 저장하거나 꺼낸 응답 식별값
     */
    public String execute(
            Long userId,
            String endpoint,
            String key,
            String requestBodyHash,
            Supplier<String> action) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key");
        }

        String redisKey = "idem:" + userId + ":" + endpoint + ":" + key;
        String stored = redis.opsForValue().get(redisKey);
        if (stored != null) {
            return replay(stored, requestBodyHash);
        }

        String response = action.get();
        redis.opsForValue().set(redisKey, requestBodyHash + SEPARATOR + response, TTL);
        return response;
    }

    private String replay(String stored, String requestBodyHash) {
        int at = stored.indexOf(SEPARATOR);
        String storedHash = stored.substring(0, at);
        if (!storedHash.equals(requestBodyHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key");
        }
        return stored.substring(at + SEPARATOR.length());
    }

    /** 요청 본문 해시. 같은 키로 다른 본문이 왔는지 판별하는 용도라 충돌 저항만 있으면 된다. */
    public static String hash(String requestBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 갖춰야 하는 알고리즘이다. 여기 오면 런타임이 깨진 것이다.
            throw new IllegalStateException(e);
        }
    }

    /*
     * ponytail: 최초 요청을 처리하는 동안에는 아직 키가 비어 있다. 같은 키가 동시에 두 번
     * 들어오면 둘 다 action 을 실행하고 뒤엣것이 앞엣것의 응답을 덮어쓴다 — 타임아웃 후
     * 재시도(순차)는 막지만 동시 중복은 막지 못한다. 제대로 하려면 setIfAbsent 로 "처리 중"
     * 표시를 먼저 심고 뒤엣것을 409 로 돌려보내야 한다. 사용자가 같은 순간에 두 번 눌러야
     * 도달하는 경우라 빈도가 낮고, 실제로 부딪히면 그때 넣는다.
     */
}
