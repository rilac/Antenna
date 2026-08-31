package ssafy.a507.backend.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/** 테스트에서 지갑 역할을 한다. MetaMask의 personal_sign과 같은 결과를 내야 한다. */
public final class WalletSignatures {

    private WalletSignatures() {}

    /** personal_sign 결과와 같은 0x + r|s|v 65바이트 hex. */
    public static String sign(String payload, Credentials credentials) {
        Sign.SignatureData data =
                Sign.signPrefixedMessage(
                        payload.getBytes(StandardCharsets.UTF_8), credentials.getEcKeyPair());
        return Numeric.toHexString(
                ByteBuffer.allocate(65)
                        .put(data.getR())
                        .put(data.getS())
                        .put(data.getV())
                        .array());
    }

    /** 지갑이 27/28 대신 0/1을 주는 구현을 흉내 낸다. */
    public static String withLegacyV(String signatureHex) {
        byte[] signature = Numeric.hexStringToByteArray(signatureHex);
        signature[64] = (byte) (signature[64] - 27);
        return Numeric.toHexString(signature);
    }
}
