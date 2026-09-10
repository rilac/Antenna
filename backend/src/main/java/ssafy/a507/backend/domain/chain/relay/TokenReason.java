package ssafy.a507.backend.domain.chain.relay;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.web3j.abi.datatypes.generated.Bytes32;

/**
 * ANT 가 움직인 사유 (ANT-CHAIN-10). {@code token_ledger.reason} 어휘이자 컨트랙트 이벤트의 {@code bytes32 reason} 이다.
 *
 * <p>이름은 TOKEN-08 이 확정한 7개 그대로다. 금액(D4)은 아직 열려 있지만 이름은 닫혔고, 새 사유는 곧 새 호출자라
 * 어차피 코드 변경이다 — 그래서 enum 이다. 문자열로 두면 33바이트·한글이 인코딩 시점에 터진다.
 *
 * <p>컨트랙트로 가는 형식은 <b>ASCII 왼쪽 정렬, 뒤를 0 으로 채운 32바이트</b>. {@code "SLOT_OVER"} 는
 * {@code 0x534c4f545f4f564552} + 0×23. 인덱서 ②(ANT-CHAIN-11)는 뒤 0 을 떼어 {@link #fromBytes32} 로 되돌린다.
 */
public enum TokenReason {
    /** 지갑 연동 시 가입 보너스 mint (TOKEN-07). */
    SIGNUP_BONUS(true),
    /** 하루 무료 슬롯 3건 초과 예측의 소각 (PRED-01 202). */
    SLOT_OVER(true),
    /** 구독 결제 — 구독자 원장의 차감. 컨트랙트에는 안 보낸다: {@code subscribe()} 에 reason 인자가 없다. */
    SUBSCRIBE(false),
    /** 구독 결제 — 예측가 원장의 수입(70%). ledger 전용. */
    SUBSCRIBE_INCOME(false),
    /** 광고 게재료 소각 (COMMUNITY-05). */
    AD_PAY(true),
    /** 시즌 참가비 소각 (SEASON-03). */
    SEASON_ENTRY(true),
    /** 시즌 상금 mint (SEASON-04). */
    SEASON_REWARD(true);

    private final boolean onChain;

    TokenReason(boolean onChain) {
        this.onChain = onChain;
    }

    /** mint/burn 의 인자로 체인에 실리는 사유인가. false 면 ledger 에만 쓰는 값이라 릴레이어가 거절한다. */
    public boolean isOnChain() {
        return onChain;
    }

    /** 컨트랙트 인자 형식. 이름이 32바이트를 넘으면 enum 선언이 잘못된 것이라 여기서 바로 터진다. */
    public Bytes32 toBytes32() {
        byte[] ascii = name().getBytes(StandardCharsets.US_ASCII);
        if (ascii.length > 32) {
            throw new IllegalStateException("reason 이 32바이트를 넘는다: " + name());
        }
        return new Bytes32(Arrays.copyOf(ascii, 32));
    }

    /** 이벤트의 bytes32 → 사유. 뒤 0 을 떼고 이름을 찾는다. 모르는 값(다른 배포·수동 tx)이면 empty. */
    public static Optional<TokenReason> fromBytes32(byte[] raw) {
        if (raw == null || raw.length != 32) {
            return Optional.empty();
        }
        int end = 32;
        while (end > 0 && raw[end - 1] == 0) {
            end--;
        }
        String name = new String(raw, 0, end, StandardCharsets.US_ASCII);
        for (TokenReason r : values()) {
            if (r.name().equals(name)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
