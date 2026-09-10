package ssafy.a507.backend.domain.chain.indexer;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.domain.chain.relay.TokenReason;

/**
 * 디코딩된 PredictToken 이벤트 한 건 (ANT-CHAIN-11). {@code Minted · Burned · Subscribed} 셋을 한 모양으로 담는다.
 *
 * <p>{@link AnchoredLog} 와 같은 이유로 web3j 의 {@code Log} 를 들고 다니지 않는다 — 가짜 소스로 테스트하려면
 * RPC 타입에서 떼어야 하고, hex 정규화(소문자·0x)는 디코더 한 곳에서 끝낸다.
 *
 * @param kind          어느 이벤트인지
 * @param from          Burned 의 from · Subscribed 의 subscriber. Minted 는 null
 * @param to            Minted 의 to · Subscribed 의 creator. Burned 는 null
 * @param amount        정수 ANT(decimals 0). Subscribed 는 구독료 전액
 * @param creatorShare  Subscribed 만. 예측가 몫(70% + 잔돈)
 * @param platformShare Subscribed 만. 플랫폼 몫 — 원장에는 안 쓴다(수납 주소는 회원이 아니다)
 * @param reasonHex     Minted/Burned 의 bytes32 원문(0x + 64 hex). Subscribed 는 null — 이벤트에 reason 이 없다
 */
public record TokenLog(
        String txHash,
        int logIndex,
        long blockNumber,
        String blockHash,
        String contractAddress,
        Kind kind,
        String from,
        String to,
        BigInteger amount,
        BigInteger creatorShare,
        BigInteger platformShare,
        String reasonHex) {

    public enum Kind {
        MINTED("Minted"),
        BURNED("Burned"),
        SUBSCRIBED("Subscribed");

        /** {@code chain_events.event_name} 에 들어가는 이름 — 컨트랙트 이벤트 이름 그대로. */
        public final String eventName;

        Kind(String eventName) {
            this.eventName = eventName;
        }

        public static Optional<Kind> ofEventName(String name) {
            for (Kind k : values()) {
                if (k.eventName.equals(name)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }

    /** 서버 어휘로 되돌린 사유. enum 에 없는 값(이관·수동 tx)이면 empty — 그때는 {@link #reasonText()} 를 쓴다. */
    public Optional<TokenReason> reason() {
        return reasonHex == null ? Optional.empty() : TokenReason.fromBytes32(Numeric.hexStringToByteArray(reasonHex));
    }

    /** bytes32 의 ASCII 원문(뒤 0 제거). {@code token_ledger.reason} varchar(32) 에 항상 들어간다. 없으면 null. */
    public String reasonText() {
        if (reasonHex == null) {
            return null;
        }
        byte[] raw = Numeric.hexStringToByteArray(reasonHex);
        int end = raw.length;
        while (end > 0 && raw[end - 1] == 0) {
            end--;
        }
        return new String(raw, 0, end, StandardCharsets.US_ASCII);
    }
}
