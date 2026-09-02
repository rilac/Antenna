package ssafy.a507.backend.domain.chain.merkle;

import java.util.List;

/**
 * 사용자에게 미리 내려주는 증명 번들 v1 — Stage 1 "포함 증명" (ANT-CHAIN-07).
 *
 * <p>서버·DB가 통째로 사라져도 사용자가 이 파일과 체인만으로 "내 커밋이 그 루트에 들어
 * 있었다"를 증명할 수 있게 하는 <b>사용자 보관용 사본</b>이다. 그래서 서버에는 저장하지
 * 않는다 — 서버에 저장한 사본은 서버와 같이 죽는다. proof는 리프 목록에서 언제든
 * 재계산되므로 발급 시점에 만들어 내려보내면 끝이다.
 *
 * <p>payload·salt는 <b>일부러 없다.</b> 앵커 직후는 만기 한참 전이고, 번들은 파일이라
 * 유출될 수 있다 — 여기 salt가 들어 있으면 만기 전 예측 내용이 새는 통로가 된다.
 * 해시 덩어리(commitHash·proof)만으로는 내용을 알 수 없어 유출돼도 무해하다.
 * 귀속 증명(Stage 2: payload·salt 첨부)은 만기 후의 일이고 PRED-02 확정에 종속이라
 * CHAIN-02 이후로 넘긴다.
 *
 * <p>{@code contractAddress}를 번들이 직접 들고 다니는 것이 중요하다 — 컨트랙트가
 * 재배포돼 주소가 갈려도(CHAIN-01 대안 F1이 감수한 것), 이미 발급된 번들은 자기가
 * 대조할 옛 주소를 정확히 가리킨다.
 *
 * <p>필드 계약은 verify.html과 공유한다. 이름을 바꾸면 이미 발급된 번들이 죽으므로
 * <b>한 번 정한 이름은 바꾸지 않는다</b> — 바꿀 일이 생기면 {@code version}을 올린다.
 */
public record ProofBundle(
        /** 스키마 버전. 하위 호환 판단 기준. 현재 1. */
        int version,
        /** 서명 payload·컨트랙트가 사는 체인. SSAFY = 31221. */
        long chainId,
        /** 이 번들을 대조할 CommitAnchor 주소(0x + 40 hex). 재배포돼도 이 번들은 이 주소를 본다. */
        String contractAddress,
        /** rootOf 호출 인자 = anchor_batches.id (CHAIN-01 대안 B1). */
        long batchId,
        /** 앵커 tx 해시. 검증에 필수는 아니고 블록 탐색용 참고 정보다. */
        String txHash,
        /** 앵커 확정 시각(ISO-8601). "이 시각 이전에 존재했다"의 그 시각. */
        String anchoredAt,
        /** 커밋 해시(0x + 64 hex). 트리 리프의 원상 — 검증기가 keccak256을 한 겹 얹어 리프를 만든다. */
        String commitHash,
        /** 형제 해시 경로, 아래에서 위로. 이 배열이 번들의 존재 이유다 — DB가 죽으면 영영 못 만든다. */
        List<String> proof) {

    public static final int CURRENT_VERSION = 1;

    /** MerkleTree 결과에서 번들을 조립한다. CHAIN-02 앵커 확정 훅이 리프마다 호출한다. */
    public static ProofBundle of(
            long chainId,
            String contractAddress,
            long batchId,
            String txHash,
            String anchoredAt,
            byte[] commitHash,
            List<byte[]> proof) {
        return new ProofBundle(
                CURRENT_VERSION,
                chainId,
                contractAddress,
                batchId,
                txHash,
                anchoredAt,
                toHex(commitHash),
                proof.stream().map(ProofBundle::toHex).toList());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
