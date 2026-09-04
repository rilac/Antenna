package ssafy.a507.backend.domain.chain.indexer;

import java.util.List;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/** 인덱서 테스트용 로그·해시 생성. 값은 결정적(seed 의 keccak)이라 테스트가 서로 비교할 수 있다. */
final class FakeLogs {

    static final String CONTRACT = "0x07f8cfe2bc6174d62be8226e5e8699ffbf0d6d6a";

    private FakeLogs() {}

    static String keccak(String seed) {
        return Numeric.toHexString(Hash.sha3(seed.getBytes()));
    }

    static String blockHashOf(long blockNumber) {
        return keccak("block-" + blockNumber);
    }

    /** batchId · 루트 · 블록으로 로그 하나. tx 해시는 batchId 로 결정된다. */
    static AnchoredLog anchored(long batchId, String merkleRoot, long blockNumber) {
        return anchored(batchId, merkleRoot, blockNumber, CONTRACT);
    }

    static AnchoredLog anchored(long batchId, String merkleRoot, long blockNumber, String contract) {
        return new AnchoredLog(
                keccak("tx-" + batchId),
                0,
                blockNumber,
                blockHashOf(blockNumber),
                contract,
                batchId,
                merkleRoot,
                List.of(keccak("leaf-" + batchId + "-0"), keccak("leaf-" + batchId + "-1")));
    }
}
