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

    /** 루트로 결정되는 tx 해시 — v3 이벤트는 루트가 키라(ANT-CHAIN-13) 테스트도 루트로 tx 를 가른다. */
    static String txOf(String merkleRoot) {
        return keccak("tx-" + merkleRoot);
    }

    /** 루트 · 블록으로 로그 하나. */
    static AnchoredLog anchored(String merkleRoot, long blockNumber) {
        return anchored(merkleRoot, blockNumber, CONTRACT);
    }

    static AnchoredLog anchored(String merkleRoot, long blockNumber, String contract) {
        return new AnchoredLog(
                txOf(merkleRoot),
                0,
                blockNumber,
                blockHashOf(blockNumber),
                contract,
                merkleRoot,
                List.of(keccak("leaf-" + merkleRoot + "-0"), keccak("leaf-" + merkleRoot + "-1")));
    }
}
