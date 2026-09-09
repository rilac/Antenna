package ssafy.a507.backend.domain.prediction.commit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커밋 봉인 규격 — 해시·형식 (ANT-PRED-02).
 *
 * <p>크로스 검증이 핵심이다. 픽스처는 ethers(JS)가 만든 기준값이고({@code contracts/scripts/gen-commit-fixture.mjs}),
 * Java 가 같은 입력에서 같은 noteHash·commitString·commitHash 를 내야 한다. 브라우저(D-03 ①단계)도 같은 파일로 맞춘다.
 * 줄 구분·개행·숫자 자릿수·UTF-8·정규화 규칙이 하나라도 어긋나면 여기서 깨진다.
 */
@DisplayName("CommitHashes · CommitPayload — 커밋 봉인 규격")
class CommitHashesTest {

    private static final String SALT_A = "0123456789abcdef".repeat(4);
    private static final String SALT_B = "fedcba9876543210".repeat(4);

    @Nested
    @DisplayName("크로스 픽스처 (ethers 기준값)")
    class Fixture {

        @Test
        @DisplayName("픽스처의 모든 케이스에서 noteHash · 커밋 문자열 · commitHash 가 ethers 와 같다")
        void 픽스처_전_케이스_일치() throws Exception {
            JsonNode fixture;
            try (InputStream in = getClass().getResourceAsStream("/commit/commit-cross-fixture.json")) {
                assertThat(in).as("commit-cross-fixture.json 이 test/resources/commit 에 있어야 한다").isNotNull();
                fixture = JsonMapper.builder().build().readTree(in);
            }
            assertThat(fixture.get("cases").size()).isGreaterThanOrEqualTo(5);

            for (JsonNode c : fixture.get("cases")) {
                JsonNode f = c.get("fields");
                String noteHash = CommitHashes.noteHash(c.get("note").asString(), c.get("noteSalt").asString());
                assertThat(noteHash).as("noteHash: %s", c.get("name").asString()).isEqualTo(c.get("noteHash").asString());

                CommitPayload payload = new CommitPayload(
                        f.get("stockCode").asString(),
                        Prediction.Direction.valueOf(f.get("direction").asString()),
                        new BigDecimal(f.get("targetPrice").asString()),
                        (short) f.get("horizon").asInt(),
                        noteHash);
                assertThat(payload.canonical())
                        .as("commitString: %s", c.get("name").asString())
                        .isEqualTo(c.get("commitString").asString());
                assertThat(CommitHashes.keccak256Hex(payload.canonical()))
                        .as("commitHash: %s", c.get("name").asString())
                        .isEqualTo(c.get("commitHash").asString());
            }
        }
    }

    @Nested
    @DisplayName("noteHash")
    class NoteHash {

        @Test
        @DisplayName("본문은 그대로 — \\r\\n 을 \\n 으로 바꾸거나 끝 공백을 지우면 다른 해시가 된다")
        void 본문_정규화_없음() {
            assertThat(CommitHashes.noteHash("a\r\nb ", SALT_A))
                    .isNotEqualTo(CommitHashes.noteHash("a\nb ", SALT_A))
                    .isNotEqualTo(CommitHashes.noteHash("a\r\nb", SALT_A));
        }

        @Test
        @DisplayName("같은 본문이라도 noteSalt 가 다르면 다른 해시 — 같은 근거를 두 번 써도 커밋이 겹치지 않는 근거")
        void noteSalt_가_다르면_다르다() {
            assertThat(CommitHashes.noteHash("실적 좋음", SALT_A)).isNotEqualTo(CommitHashes.noteHash("실적 좋음", SALT_B));
        }

        @Test
        @DisplayName("빈 본문도 된다 — keccak(noteSalt) 라 여전히 난수다")
        void 빈_본문() {
            assertThat(CommitHashes.noteHash("", SALT_A)).matches("^0x[0-9a-f]{64}$");
        }
    }

    @Nested
    @DisplayName("noteSalt 형식 검사")
    class NoteSalt {

        @Test
        @DisplayName("64 hex 소문자만 통과한다")
        void 정상() {
            assertThat(CommitHashes.requireNoteSalt(SALT_A)).isEqualTo(SALT_A);
        }

        @Test
        @DisplayName("0x 접두·대문자·길이 불일치·null 은 INVALID_REQUEST(noteSalt)")
        void 형식_불일치() {
            for (String bad : new String[] {null, "0x" + SALT_A.substring(2), SALT_A.toUpperCase(), SALT_A.substring(1), SALT_A + "0"}) {
                assertThatThrownBy(() -> CommitHashes.requireNoteSalt(bad))
                        .as("noteSalt=%s", bad)
                        .isInstanceOf(BusinessException.class)
                        .satisfies(e -> {
                            assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                            assertThat(((BusinessException) e).getField()).isEqualTo("noteSalt");
                        });
            }
        }

        @Test
        @DisplayName("모든 바이트가 같은 값(000…0, ffff…)은 거절 — 배열만 만들고 안 채운 프론트 버그를 잡는다")
        void 전부_같은_바이트() {
            for (String bad : new String[] {"00".repeat(32), "ff".repeat(32), "a5".repeat(32)}) {
                assertThatThrownBy(() -> CommitHashes.requireNoteSalt(bad)).isInstanceOf(BusinessException.class);
            }
            // 한 바이트만 달라도 통과 — 품질 검사가 아니라 버그 검출이다.
            assertThat(CommitHashes.requireNoteSalt("00".repeat(31) + "01")).isNotNull();
        }
    }

    @Nested
    @DisplayName("커밋 문자열")
    class Canonical {

        private CommitPayload payload(BigDecimal price) {
            return new CommitPayload("005930", Prediction.Direction.UP, price, (short) 30, "0x" + "ab".repeat(32));
        }

        @Test
        @DisplayName("6줄 · \\n 구분 · 끝 개행 없음 · salt 줄 없음")
        void 모양() {
            String s = payload(new BigDecimal("82000")).canonical();
            assertThat(s).isEqualTo(
                    "antenna:commit:v1\nstockCode=005930\ndirection=UP\ntargetPrice=82000.00\nhorizon=30\nnoteHash=0x"
                            + "ab".repeat(32));
            assertThat(s).doesNotEndWith("\n").doesNotContain("salt=").doesNotContain("createdAt");
        }

        @Test
        @DisplayName("targetPrice 는 항상 소수 둘째 자리 — 82000 · 82000.0 · 82000.00 이 같은 문자열이 된다")
        void 가격_자릿수_고정() {
            String a = payload(new BigDecimal("82000")).canonical();
            String b = payload(new BigDecimal("82000.0")).canonical();
            String c = payload(new BigDecimal("82000.00")).canonical();
            assertThat(a).isEqualTo(b).isEqualTo(c);
            assertThat(payload(new BigDecimal("1E+5")).canonical()).contains("targetPrice=100000.00");
        }

        @Test
        @DisplayName("셋째 자리 이하가 있으면 반올림하지 않고 INVALID_REQUEST(targetPrice) — 조용히 고치면 서명과 어긋난다")
        void 가격_반올림_거절() {
            assertThatThrownBy(() -> payload(new BigDecimal("82000.005")).canonical())
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getField()).isEqualTo("targetPrice"));
        }

        @Test
        @DisplayName("종목코드가 없으면 만들지 않는다 — 예측은 실전(REAL) 전용")
        void 종목코드_필수() {
            assertThatThrownBy(() -> new CommitPayload(
                            null, Prediction.Direction.UP, BigDecimal.TEN, (short) 7, "0x" + "00".repeat(32)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("lines() 는 서명 문자열(PRED-01)이 그대로 쓰는 다섯 줄이다")
        void lines_다섯_줄() {
            assertThat(payload(new BigDecimal("82000")).lines())
                    .containsExactly(
                            "stockCode=005930",
                            "direction=UP",
                            "targetPrice=82000.00",
                            "horizon=30",
                            "noteHash=0x" + "ab".repeat(32));
        }
    }
}
