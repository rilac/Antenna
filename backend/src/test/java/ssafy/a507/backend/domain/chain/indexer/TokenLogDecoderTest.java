package ssafy.a507.backend.domain.chain.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;
import ssafy.a507.backend.domain.chain.relay.TokenReason;

/**
 * ANT-CHAIN-11 — SSAFY 체인에서 뜬 <b>실제</b> PredictToken 로그(CHAIN-03 배포 직후 mint 1000 · burn 1000)를 그대로 디코딩한다.
 *
 * <p>픽스처는 2026-09-10 {@code eth_getLogs} 원본이다(topics·data 를 손대지 않았다). {@code Subscribed} 는 아직 체인에
 * 실기가 없어 인자를 규격대로 손으로 인코딩했다 — 첫 구독 tx 가 나오면 그 원본으로 바꾼다.
 */
@DisplayName("PredictToken 로그 디코딩")
class TokenLogDecoderTest {

    static final String TOKEN = "0xe11d728b157240DCf4a8c831176D248BAFD33077";
    static final String TOPIC_MINTED = "0xc263b302aec62d29105026245f19e16f8e0137066ccd4a8bd941f716bd4096bb";
    static final String TOPIC_BURNED = "0x265f3479df3fade4f6c5d2935e2beb9fb3166b4842cd4703862f604ac1b708a3";
    static final String WALLET = "0x97955396fa4be2e057575e79b91e8f1855629dc0";
    static final String WALLET_TOPIC = "0x00000000000000000000000097955396fa4be2e057575e79b91e8f1855629dc0";
    static final String REASON_SIGNUP = "0x5349474e55505f424f4e55530000000000000000000000000000000000000000";
    static final String REASON_SLOT = "0x534c4f545f4f5645520000000000000000000000000000000000000000000000";
    static final String AMOUNT_1000 = "0x00000000000000000000000000000000000000000000000000000000000003e8";

    static final String MINT_TX = "0x663377b8b1d697ec5bd97b8357ea40a30e6c408f20e3d07da3dd087554bda15b";
    static final String MINT_BLOCK_HASH = "0x4fd29449f251247b4fed438eb360130ecb8641d05ffde68087b4bcec66907a4f";
    static final String BURN_TX = "0x7f930bd514d4d558746f21313b9f0459d185afbeef821b234e77b86aff82ed88";
    static final String BURN_BLOCK_HASH = "0xce130891d1b194d7c00845329cac28c227d93d02c04004eeb639fdb464883aad";

    static Log log(String tx, String blockHash, long block, int idx, List<String> topics, String data) {
        return new Log(
                false,
                Numeric.toHexStringWithPrefix(BigInteger.valueOf(idx)),
                "0x0",
                tx,
                blockHash,
                Numeric.toHexStringWithPrefix(BigInteger.valueOf(block)),
                TOKEN, // 노드가 준 그대로(체크섬 대문자) — 디코더의 소문자 정규화도 같이 본다
                data,
                "mined",
                topics);
    }

    /** 블록 11233493 · logIndex 1 — README 흔적표 첫 행. */
    static Log mint1000() {
        return log(MINT_TX, MINT_BLOCK_HASH, 11_233_493L, 1, List.of(TOPIC_MINTED, WALLET_TOPIC, REASON_SIGNUP), AMOUNT_1000);
    }

    /** 블록 11233494 · logIndex 1 — README 흔적표 둘째 행. */
    static Log burn1000() {
        return log(BURN_TX, BURN_BLOCK_HASH, 11_233_494L, 1, List.of(TOPIC_BURNED, WALLET_TOPIC, REASON_SLOT), AMOUNT_1000);
    }

    @Test
    @DisplayName("이벤트 시그니처 topic0 셋 — Minted·Burned 는 실체인 로그의 값과 같다")
    void topic0_고정() {
        assertThat(TokenLogDecoder.TOPIC_MINTED).isEqualTo(TOPIC_MINTED);
        assertThat(TokenLogDecoder.TOPIC_BURNED).isEqualTo(TOPIC_BURNED);
        assertThat(TokenLogDecoder.TOPICS).hasSize(3).doesNotHaveDuplicates();
        // Subscribed(address,address,uint256,uint256,uint256) 의 keccak — 컨트랙트 정의 순서대로
        assertThat(TokenLogDecoder.TOPIC_SUBSCRIBED)
                .isEqualTo(Numeric.toHexString(org.web3j.crypto.Hash.sha3("Subscribed(address,address,uint256,uint256,uint256)".getBytes())));
    }

    @Test
    @DisplayName("Minted 실로그 — to·amount·reason 이 흔적표와 같고 주소·해시는 소문자로 눕는다")
    void minted() {
        Optional<TokenLog> decoded = TokenLogDecoder.decode(mint1000());

        assertThat(decoded).isPresent();
        TokenLog l = decoded.get();
        assertThat(l.kind()).isEqualTo(TokenLog.Kind.MINTED);
        assertThat(l.to()).isEqualTo(WALLET);
        assertThat(l.from()).isNull();
        assertThat(l.amount()).isEqualTo(BigInteger.valueOf(1000));
        assertThat(l.reasonHex()).isEqualTo(REASON_SIGNUP);
        assertThat(l.reason()).contains(TokenReason.SIGNUP_BONUS);
        assertThat(l.reasonText()).isEqualTo("SIGNUP_BONUS");
        assertThat(l.txHash()).isEqualTo(MINT_TX);
        assertThat(l.logIndex()).isEqualTo(1);
        assertThat(l.blockNumber()).isEqualTo(11_233_493L);
        assertThat(l.blockHash()).isEqualTo(MINT_BLOCK_HASH);
        assertThat(l.contractAddress()).isEqualTo(TOKEN.toLowerCase());
        assertThat(l.creatorShare()).isNull();
    }

    @Test
    @DisplayName("Burned 실로그 — from·amount·reason SLOT_OVER")
    void burned() {
        TokenLog l = TokenLogDecoder.decode(burn1000()).orElseThrow();

        assertThat(l.kind()).isEqualTo(TokenLog.Kind.BURNED);
        assertThat(l.from()).isEqualTo(WALLET);
        assertThat(l.to()).isNull();
        assertThat(l.amount()).isEqualTo(BigInteger.valueOf(1000));
        assertThat(l.reason()).contains(TokenReason.SLOT_OVER);
        assertThat(l.blockNumber()).isEqualTo(11_233_494L);
    }

    @Test
    @DisplayName("Subscribed — subscriber·creator·amount·creatorShare·platformShare, reason 은 없다")
    void subscribed() {
        String subscriber = "0x00000000000000000000000000000000000000aa";
        String creator = "0x00000000000000000000000000000000000000bb";
        String data =
                "0x"
                        + "0000000000000000000000000000000000000000000000000000000000007530" // 30000
                        + "0000000000000000000000000000000000000000000000000000000000005208" // 21000
                        + "0000000000000000000000000000000000000000000000000000000000002328"; // 9000
        Log raw =
                log(
                        "0x" + "11".repeat(32),
                        "0x" + "22".repeat(32),
                        11_300_000L,
                        0,
                        List.of(
                                TokenLogDecoder.TOPIC_SUBSCRIBED,
                                "0x000000000000000000000000" + subscriber.substring(2),
                                "0x000000000000000000000000" + creator.substring(2)),
                        data);

        TokenLog l = TokenLogDecoder.decode(raw).orElseThrow();

        assertThat(l.kind()).isEqualTo(TokenLog.Kind.SUBSCRIBED);
        assertThat(l.from()).isEqualTo(subscriber);
        assertThat(l.to()).isEqualTo(creator);
        assertThat(l.amount()).isEqualTo(BigInteger.valueOf(30_000));
        assertThat(l.creatorShare()).isEqualTo(BigInteger.valueOf(21_000));
        assertThat(l.platformShare()).isEqualTo(BigInteger.valueOf(9_000));
        assertThat(l.reasonHex()).isNull();
        assertThat(l.reason()).isEmpty();
        assertThat(l.reasonText()).isNull();
    }

    @Test
    @DisplayName("모르는 topic0(Transfer 등)은 empty — 필터를 믿지 않고 한 번 더 거른다")
    void unknown_topic() {
        Log transfer =
                log(MINT_TX, MINT_BLOCK_HASH, 11_233_493L, 0,
                        List.of("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef", "0x" + "00".repeat(32), WALLET_TOPIC),
                        AMOUNT_1000);
        assertThat(TokenLogDecoder.decode(transfer)).isEmpty();
    }

    @Test
    @DisplayName("enum 에 없는 reason(MIGRATION) 은 reason() 이 empty 고 reasonText() 가 원문이다")
    void unknown_reason() {
        String migration = "0x4d4947524154494f4e" + "00".repeat(23);
        TokenLog l =
                TokenLogDecoder.decode(
                                log(MINT_TX, MINT_BLOCK_HASH, 11_233_493L, 1, List.of(TOPIC_MINTED, WALLET_TOPIC, migration), AMOUNT_1000))
                        .orElseThrow();
        assertThat(l.reason()).isEmpty();
        assertThat(l.reasonText()).isEqualTo("MIGRATION");
    }
}
