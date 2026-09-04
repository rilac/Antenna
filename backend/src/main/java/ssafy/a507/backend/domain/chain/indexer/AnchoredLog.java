package ssafy.a507.backend.domain.chain.indexer;

import java.util.List;

/**
 * 디코딩된 {@code Anchored(uint256 indexed batchId, bytes32 merkleRoot, bytes32[] commitHashes)} 로그 한 건 (ANT-CHAIN-04).
 *
 * <p>web3j 의 {@code Log} 를 그대로 들고 다니지 않는 이유는 둘이다 — 인덱서 로직이 RPC 타입에 묶이면
 * 가짜 소스로 테스트할 수 없고, hex 문자열의 대소문자·0x 접두 같은 정규화를 한 곳(디코더)에서 끝내고 싶다.
 *
 * @param txHash          0x + 64 hex, 소문자
 * @param logIndex        tx 안의 로그 순번. tx_hash 와 함께 UQ
 * @param blockNumber     로그가 실린 블록
 * @param blockHash       그 블록의 해시. reorg 검사용으로 payload 에 함께 남긴다
 * @param contractAddress 이벤트를 낸 컨트랙트(소문자). 재배포 뒤 옛 주소의 이벤트를 가르는 키
 * @param batchId         온체인 batchId = anchor_batches.id
 * @param merkleRoot      0x + 64 hex, 소문자 — anchor_batches.merkle_root 와 같은 형식
 * @param commitHashes    리프 순서 그대로의 커밋 해시(0x + 64 hex, 소문자)
 */
public record AnchoredLog(
        String txHash,
        int logIndex,
        long blockNumber,
        String blockHash,
        String contractAddress,
        long batchId,
        String merkleRoot,
        List<String> commitHashes) {}
