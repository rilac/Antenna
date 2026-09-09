package ssafy.a507.backend.domain.prediction.commit;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;

/**
 * 커밋 봉인에 쓰는 해시·형식 규칙 한 곳 (ANT-PRED-02).
 *
 * <p>해시는 전부 <b>keccak256</b>(web3j {@link Hash#sha3})이다. 머클 트리·컨트랙트·브라우저(ethers)가 같은 함수를 쓰므로
 * 브라우저 하나로 세 단계 검산이 전부 된다. {@code MessageDigest("SHA3-256")}은 패딩이 다른 <b>다른 함수</b>라 쓰면 안 된다.
 *
 * <p>이 시스템의 난수는 {@code noteSalt} 하나뿐이다(09-09 결정 — 커밋 salt 를 따로 두지 않는다).
 * {@code noteHash = keccak256(note ‖ noteSalt)} 가 noteSalt 의 엔트로피를 물려받아 균등 난수처럼 보이고,
 * 그 값이 커밋 문자열 안에 들어가므로 바깥 commitHash 도 사전 공격이 안 된다. <b>noteSalt 를 없애거나 약하게 만들면
 * 커밋 전체가 다시 추측 가능해진다</b> — 이 클래스가 형식을 지키는 이유다.
 */
public final class CommitHashes {

    /** 32바이트 = 소문자 64 hex, 0x 없음. ERD {@code prediction_commits.salt varchar(64)} 에 그대로 들어간다. */
    private static final Pattern NOTE_SALT = Pattern.compile("^[0-9a-f]{64}$");

    private CommitHashes() {}

    /** UTF-8 바이트를 keccak256 → {@code 0x} + 소문자 64 hex. */
    public static String keccak256Hex(String utf8) {
        return Numeric.toHexString(Hash.sha3(utf8.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 근거 해시. {@code keccak256(utf8(note) ‖ utf8(noteSalt))}.
     *
     * <p>본문은 받은 바이트 <b>그대로</b>다 — trim 도, {@code \r\n} → {@code \n} 정규화도 하지 않는다. 검증하는 브라우저가
     * 서버와 같은 바이트를 만들려면 "손대지 않는다"가 유일하게 재현 가능한 규칙이다. noteSalt 는 hex <b>문자열</b>로 이어 붙인다
     * (바이트로 디코드하지 않는다). 구분자는 없다.
     */
    public static String noteHash(String note, String noteSalt) {
        return keccak256Hex(note + requireNoteSalt(noteSalt));
    }

    /**
     * noteSalt 형식 검사. 형식(64 hex)과 "모든 바이트가 같은 값"(예: {@code 000…0})만 거절한다.
     *
     * <p>후자는 난수 품질 검사가 아니다 — {@code new Uint8Array(32)} 만 만들고 채우는 걸 잊은 프론트 버그를 개발 중에 바로 잡기 위한
     * 것이다. 진짜 약한 난수는 서버가 알 수 없고, 그 피해는 작성자 본인(자기 예측의 조기 노출)에게만 간다.
     */
    public static String requireNoteSalt(String noteSalt) {
        if (noteSalt == null || !NOTE_SALT.matcher(noteSalt).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "noteSalt");
        }
        String first = noteSalt.substring(0, 2);
        boolean allSame = true;
        for (int i = 2; i < noteSalt.length(); i += 2) {
            if (!noteSalt.startsWith(first, i)) {
                allSame = false;
                break;
            }
        }
        if (allSame) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "noteSalt");
        }
        return noteSalt;
    }
}
