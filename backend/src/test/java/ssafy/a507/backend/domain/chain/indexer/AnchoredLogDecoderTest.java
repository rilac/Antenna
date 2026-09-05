package ssafy.a507.backend.domain.chain.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Log;

/**
 * ANT-CHAIN-04 — SSAFY 체인에서 뜬 <b>실제</b> Anchored 로그(batchId 4, CHAIN-02 실기 배치)를 그대로 디코딩한다.
 *
 * <p>픽스처는 2026-09-04 {@code eth_getLogs} 원본이다(topics·data 를 손대지 않았다). 여기서 깨지면 이벤트 정의가
 * 배포된 컨트랙트와 어긋난 것이다 — ABI 리소스 검사({@code CommitAnchorConfigTest})가 "이름·타입" 을, 이 테스트가 "실제 바이트" 를 고정한다.
 */
@DisplayName("Anchored 로그 디코딩")
class AnchoredLogDecoderTest {

    static final String CONTRACT = "0x07f8cfe2bc6174d62be8226e5e8699ffbf0d6d6a";
    static final String TOPIC0 = "0xd79663e5531097d358410f95abf49ddafeb9c8c0a24396250128deeb9c30a0f0";
    static final String TX4 = "0xba1cde6bf5c9fb3dd12ba45809cdb779819f2db98c901f50e949aec3e5bd2fb7";
    static final String BLOCK_HASH4 = "0xdb43a2aedb623743404f915a98f0c2243f654bf485116e59171e14c57d1a6d9c";
    static final String ROOT4 = "0xe44f2a9fe6f29fd389ca2749d94c25cb3eb9935e50fdf706fb55105e5bf241e2";
    static final String DATA4 =
            "0x"
                    + "e44f2a9fe6f29fd389ca2749d94c25cb3eb9935e50fdf706fb55105e5bf241e2" // merkleRoot
                    + "0000000000000000000000000000000000000000000000000000000000000040" // offset of commitHashes
                    + "0000000000000000000000000000000000000000000000000000000000000002" // length 2
                    + "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"
                    + "b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2";

    /** batchId 4 로그 원본. 주소·해시는 노드가 준 그대로(체크섬 대문자 섞임)라 디코더의 소문자 정규화도 같이 본다. */
    static Log batch4Log() {
        return new Log(
                false,
                "0x0",
                "0x0",
                TX4.toUpperCase().replace("0X", "0x"),
                BLOCK_HASH4,
                "0xaab5ee", // 11187694
                "0x07f8CfE2bc6174D62BE8226E5e8699ffBf0d6D6a",
                DATA4,
                "mined",
                List.of(TOPIC0, "0x0000000000000000000000000000000000000000000000000000000000000004"));
    }

    @Test
    @DisplayName("이벤트 시그니처 topic0 은 실체인 로그의 것과 같다")
    void topic0_고정() {
        assertThat(AnchoredLogDecoder.TOPIC).isEqualTo(TOPIC0);
    }

    @Test
    @DisplayName("batchId 4 로그 → batchId·루트·리프 2개·블록·tx·주소(소문자)")
    void 실체인_로그_디코딩() {
        Optional<AnchoredLog> decoded = AnchoredLogDecoder.decode(batch4Log());

        assertThat(decoded).isPresent();
        AnchoredLog l = decoded.get();
        assertThat(l.batchId()).isEqualTo(4L);
        assertThat(l.merkleRoot()).isEqualTo(ROOT4);
        assertThat(l.commitHashes())
                .containsExactly(
                        "0xa1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1",
                        "0xb2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2");
        assertThat(l.blockNumber()).isEqualTo(11_187_694L);
        assertThat(l.logIndex()).isZero();
        assertThat(l.txHash()).isEqualTo(TX4);
        assertThat(l.blockHash()).isEqualTo(BLOCK_HASH4);
        assertThat(l.contractAddress()).isEqualTo(CONTRACT);
    }

    @Test
    @DisplayName("topic0 이 다른 로그(RoleGranted 등)는 empty — 예외가 아니다")
    void 다른_이벤트는_거른다() {
        Log other =
                new Log(
                        false,
                        "0x1",
                        "0x0",
                        TX4,
                        BLOCK_HASH4,
                        "0xaab5ee",
                        CONTRACT,
                        "0x",
                        "mined",
                        List.of(
                                "0x2f8788117e7eff1d82e926ec794901d17c78024a50270940304540a733656f0d", // RoleGranted
                                "0x0000000000000000000000000000000000000000000000000000000000000000",
                                "0x000000000000000000000000b7f2de5b4821ee386aeac037b096f28693ca2a47",
                                "0x00000000000000000000000022b51ff89e742234a0a107c5e3e8b487d91ddea1"));
        assertThat(AnchoredLogDecoder.decode(other)).isEmpty();
    }
}
