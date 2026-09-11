package ssafy.a507.backend.domain.chain.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Log;

/**
 * ANT-CHAIN-04 · v3 ANT-CHAIN-13 — {@code Anchored(bytes32 indexed merkleRoot, bytes32[] commitHashes)} 로그를 바이트 그대로 디코딩한다.
 *
 * <p>topic0 기대값은 <b>ethers 가 따로 계산한 값</b>이다({@code id("Anchored(bytes32,bytes32[])")}) — web3j 의 EventEncoder 와
 * 두 구현을 맞대 본다. 로그 바이트는 ABI 규칙대로 손으로 만들었다: 루트는 topics[1], data 는 배열(오프셋·길이·원소).
 * v2 시절엔 SSAFY 에서 뜬 실제 로그를 픽스처로 썼는데, v3 는 운영 컨트랙트에 테스트 흔적을 남기지 않으려고 손으로 만든다.
 */
@DisplayName("Anchored 로그 디코딩")
class AnchoredLogDecoderTest {

    static final String CONTRACT = "0x8fdb4010b120dff5c2d9b4c990821aea00331c7e";
    /** ethers id("Anchored(bytes32,bytes32[])") — 2026-09-11 계산. */
    static final String TOPIC0 = "0xa2f552c13474c3d3f50172ab676ef7e60b356dca599631d9a0df9fe400b3e20a";
    /** v2 의 topic0(Anchored(uint256,bytes32,bytes32[])). 이름이 같아도 시그니처가 달라 걸러져야 한다. */
    static final String TOPIC0_V2 = "0xd79663e5531097d358410f95abf49ddafeb9c8c0a24396250128deeb9c30a0f0";
    static final String TX = "0xba1cde6bf5c9fb3dd12ba45809cdb779819f2db98c901f50e949aec3e5bd2fb7";
    static final String BLOCK_HASH = "0xdb43a2aedb623743404f915a98f0c2243f654bf485116e59171e14c57d1a6d9c";
    static final String ROOT = "0xe44f2a9fe6f29fd389ca2749d94c25cb3eb9935e50fdf706fb55105e5bf241e2";
    static final String DATA =
            "0x"
                    + "0000000000000000000000000000000000000000000000000000000000000020" // offset of commitHashes
                    + "0000000000000000000000000000000000000000000000000000000000000002" // length 2
                    + "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"
                    + "b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2";

    /** 주소·해시는 노드가 주는 대로(체크섬 대문자 섞임)라 디코더의 소문자 정규화도 같이 본다. */
    static Log anchoredLog() {
        return new Log(
                false,
                "0x0",
                "0x0",
                TX.toUpperCase().replace("0X", "0x"),
                BLOCK_HASH,
                "0xab8a6a", // 11242090
                "0x8fDb4010b120DFf5c2d9b4c990821aeA00331C7e",
                DATA,
                "mined",
                List.of(TOPIC0, ROOT.toUpperCase().replace("0X", "0x")));
    }

    @Test
    @DisplayName("이벤트 시그니처 topic0 은 ethers 가 계산한 v3 값과 같다")
    void topic0_고정() {
        assertThat(AnchoredLogDecoder.TOPIC).isEqualTo(TOPIC0);
    }

    @Test
    @DisplayName("로그 → 루트(topics[1])·리프 2개·블록·tx·주소(소문자)")
    void 로그_디코딩() {
        Optional<AnchoredLog> decoded = AnchoredLogDecoder.decode(anchoredLog());

        assertThat(decoded).isPresent();
        AnchoredLog l = decoded.get();
        assertThat(l.merkleRoot()).isEqualTo(ROOT);
        assertThat(l.commitHashes())
                .containsExactly(
                        "0xa1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1",
                        "0xb2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2");
        assertThat(l.blockNumber()).isEqualTo(11_242_090L);
        assertThat(l.logIndex()).isZero();
        assertThat(l.txHash()).isEqualTo(TX);
        assertThat(l.blockHash()).isEqualTo(BLOCK_HASH);
        assertThat(l.contractAddress()).isEqualTo(CONTRACT);
    }

    @Test
    @DisplayName("v2 Anchored 로그(topic0 이 다르다)는 empty — 옛 컨트랙트 이벤트가 v3 로 잘못 읽히지 않는다")
    void v2_로그는_거른다() {
        Log v2 =
                new Log(
                        false,
                        "0x0",
                        "0x0",
                        TX,
                        BLOCK_HASH,
                        "0xaab5ee",
                        CONTRACT,
                        "0x" + ROOT.substring(2) + DATA.substring(2),
                        "mined",
                        List.of(TOPIC0_V2, "0x0000000000000000000000000000000000000000000000000000000000000004"));
        assertThat(AnchoredLogDecoder.decode(v2)).isEmpty();
    }

    @Test
    @DisplayName("topic0 이 다른 로그(RoleGranted 등)는 empty — 예외가 아니다")
    void 다른_이벤트는_거른다() {
        Log other =
                new Log(
                        false,
                        "0x1",
                        "0x0",
                        TX,
                        BLOCK_HASH,
                        "0xab8a6a",
                        CONTRACT,
                        "0x",
                        "mined",
                        List.of(
                                "0x2f8788117e7eff1d82e926ec794901d17c78024a50270940304540a733656f0d", // RoleGranted
                                "0x0000000000000000000000000000000000000000000000000000000000000000",
                                "0x00000000000000000000000055085f3f5568ed3936a734dbaef179f8045322e3",
                                "0x000000000000000000000000ed9a22d3c39e5ddf161638b82a18a96134707491"));
        assertThat(AnchoredLogDecoder.decode(other)).isEmpty();
    }
}
