package ssafy.a507.backend.domain.chain.relay;

import java.math.BigInteger;
import ssafy.a507.backend.common.error.BusinessException;

/**
 * PredictToken(ANT) 전송기 (ANT-CHAIN-10).
 *
 * <p>인터페이스로 뺀 이유는 {@link AnchorRelayer} 와 같다 — 진입점({@code TokenOperationService})의 상태 전이
 * (PENDING → tx_hash 채움 / FAILED + 어떤 상태코드)는 체인 없이 검증돼야 하고, 그러려면 "보냈다 / 잔액 부족 /
 * RPC 장애 / 컨트랙트가 거부" 를 마음대로 내는 가짜가 필요하다.
 *
 * <p>금액은 <b>정수 ANT</b>(decimals 0, ANT-CHAIN-03). 10¹⁸ 을 곱하지 않는다. 주소는 {@code users.wallet_address}
 * 형식(0x + 40 hex, 소문자)을 그대로 받는다.
 *
 * <p>세 전송 메서드는 <b>receipt 를 기다리지 않는다.</b> tx 해시가 돌아오면 노드가 풀에 받았다는 뜻이고,
 * 채굴·확정은 인덱서 ②(ANT-CHAIN-11)가 이벤트로 본다. 확정 판정 주체를 둘로 만들지 않는다.
 *
 * <p>공통 예외:
 * <ul>
 *   <li>{@link BusinessException} CHAIN_UNAVAILABLE — 릴레이어 꺼짐 · RPC 장애 · 노드 거부 · 키에 OPERATOR_ROLE 없음</li>
 *   <li>{@link BusinessException} INSUFFICIENT_BALANCE — 시뮬레이션에서 {@code ERC20InsufficientBalance}</li>
 *   <li>{@link TokenRevertException} — 그 밖의 컨트랙트 거부({@code SelfSubscribe} 등). 호출자 검증을 뚫은 서버 버그</li>
 *   <li>{@link IllegalArgumentException} — 금액 0 이하·uint256 초과, 주소 형식, ledger 전용 reason. 호출자 버그</li>
 * </ul>
 */
public interface TokenRelayer {

    /** RPC·키·토큰 주소가 갖춰져 tx 를 보낼 수 있는지. 아니면 전송 메서드는 CHAIN_UNAVAILABLE 을 던진다. */
    boolean isEnabled();

    /** {@code mint(to, amount, reason)}. @return tx 해시 */
    String mint(String to, BigInteger amount, TokenReason reason);

    /** {@code burn(from, amount, reason)} — 오퍼레이터가 보유자 승인 없이 태운다(동의는 EIP-191 서명, AUTH-06). @return tx 해시 */
    String burn(String from, BigInteger amount, TokenReason reason);

    /** {@code subscribe(subscriber, creator, amount)} — 70:30 을 한 tx 에서. @return tx 해시 */
    String subscribe(String subscriber, String creator, BigInteger amount);

    /** 온체인 잔액(정수 ANT). eth_call 이라 tx 가 없다. */
    BigInteger balanceOf(String address);
}
