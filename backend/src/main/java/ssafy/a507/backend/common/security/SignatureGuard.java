package ssafy.a507.backend.common.security;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;

/**
 * 지갑 서명 검증의 단일 관문 (ANT-AUTH-06).
 *
 * <p>규격은 EIP-191 personal_sign이다. 서명은 암호화가 아니라서 원문을 되돌려 읽는 게 아니라,
 * 서버가 조립한 원문과 서명으로 <b>서명자 주소를 복원</b>한다. 원문이 1바이트만 달라도
 * 예외가 아니라 <b>엉뚱한 주소</b>가 나오므로, 복원 뒤의 대조가 유일한 방어선이다.
 */
@Component
public class SignatureGuard {

    /** r(32) + s(32) + v(1). */
    private static final int SIGNATURE_BYTES = 65;

    private final SignatureNonceStore nonceStore;
    private final UserRepository userRepository;

    /** payload에 박아 다른 체인에서 받은 서명의 재사용을 막는다. 기본값은 로컬 Hardhat(D4 확정 전). */
    private final long chainId;

    public SignatureGuard(
            SignatureNonceStore nonceStore,
            UserRepository userRepository,
            @Value("${CHAIN_ID:31337}") long chainId) {
        this.nonceStore = nonceStore;
        this.userRepository = userRepository;
        this.chainId = chainId;
    }

    /**
     * 지갑 연동용. 이 시점에는 대조할 users.wallet_address가 아직 NULL이라 복원까지만 한다.
     * 실패해도 nonce는 이미 소비된 상태다 — 재시도로 서명을 갈아 끼우며 맞춰 보는 걸 막는다.
     */
    public String recover(Long userId, WalletSigned request) {
        String nonce = nonceStore.consume(userId);
        if (nonce == null) {
            throw new BusinessException(ErrorCode.NONCE_NOT_FOUND);
        }
        return recoverAddress(request.signingPayload(nonce, chainId), request.signature());
    }

    /**
     * 온체인 동반 요청용. 복원 주소를 연동 지갑과 대조한다.
     * 지갑 미연동은 nonce를 태우기 전에 먼저 걸러 낸다 — 성공할 수 없는 요청에 nonce를 쓰지 않는다.
     */
    public String verify(Long userId, WalletSigned request) {
        // Optional.map으로 필드를 바로 꺼내면 안 된다 — 지갑이 NULL일 때 Optional이 비어
        // "계정 없음"과 "지갑 미연동"이 같은 오류로 뭉개진다. 프론트는 후자에서 연동 화면으로 보낸다.
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        String linked = user.getWalletAddress();
        if (linked == null) {
            throw new BusinessException(ErrorCode.WALLET_NOT_LINKED);
        }

        String recovered = recover(userId, request);
        // 저장 시점에 소문자로 눕혀 두므로 equals로 충분하다.
        if (!linked.equals(recovered)) {
            throw new BusinessException(ErrorCode.SIGNER_MISMATCH, "signature");
        }
        return recovered;
    }

    /** 복원된 주소를 0x + 소문자 40자로 돌려준다. 저장·비교 형식이 이것 하나다. */
    private String recoverAddress(String payload, String signatureHex) {
        byte[] signature;
        try {
            signature = Numeric.hexStringToByteArray(signatureHex);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "signature");
        }
        if (signature.length != SIGNATURE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "signature");
        }

        byte v = signature[SIGNATURE_BYTES - 1];
        // 지갑은 27/28을 주지만 일부 라이브러리는 0/1을 준다. web3j는 27/28을 기대한다.
        if (v < 27) {
            v += 27;
        }
        Sign.SignatureData signatureData =
                new Sign.SignatureData(
                        v, Arrays.copyOfRange(signature, 0, 32), Arrays.copyOfRange(signature, 32, 64));

        try {
            // signedPrefixedMessageToKey가 "\x19Ethereum Signed Message:\n{len}"을 붙이고
            // keccak256한 뒤 공개키를 복원한다 — personal_sign과 짝이다.
            BigInteger publicKey =
                    Sign.signedPrefixedMessageToKey(
                            payload.getBytes(StandardCharsets.UTF_8), signatureData);
            return "0x" + Keys.getAddress(publicKey);
        } catch (SignatureException e) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "signature");
        }
    }
}
