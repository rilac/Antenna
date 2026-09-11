package ssafy.a507.backend.domain.chain.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.a507.backend.domain.chain.config.ChainProperties;
import ssafy.a507.backend.domain.chain.config.CommitAnchorProperties;
import ssafy.a507.backend.domain.chain.config.PredictTokenProperties;

/** ANT-CHAIN-12 — 부팅 시 역할 확인. 체인 없이 볼 수 있는 것만. 실체인은 {@code ChainRoleCheckE2ELiveTest}. */
@DisplayName("부팅 시 체인 역할 확인(ChainRoleCheck)")
class ChainRoleCheckTest {

    private static final String KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String ANCHOR = "0x8fdb4010b120dff5c2d9b4c990821aea00331c7e";
    private static final String TOKEN = "0xeee56721cd2c0383139756c88b6db06024a412cb";

    @Test
    @DisplayName("역할 해시가 컨트랙트 상수와 같다 — ethers id() 로 따로 계산한 값과 대조")
    void 역할_해시() {
        assertThat(ChainRoleCheck.ANCHOR_ROLE)
                .isEqualTo("0x08b5ce2e3163e37059f807346dad4dd6235ed44f92dced22992662cb45706362");
        assertThat(ChainRoleCheck.OPERATOR_ROLE)
                .isEqualTo("0x97667070c54ef182b0f5858b034beac1b6f3089aa2d3188bb1e8929f4fa9b929");
    }

    @Test
    @DisplayName("hasRole 호출 데이터 — 셀렉터 0x91d14854 · 역할 32바이트 · 주소 32바이트")
    void hasRole_호출_데이터() {
        String account = "0x55085F3F5568ED3936A734DbaEF179F8045322E3";

        String data = ChainRoleCheck.hasRoleCall(ChainRoleCheck.ANCHOR_ROLE, account);

        assertThat(data).startsWith("0x91d14854").hasSize(2 + 8 + 64 + 64);
        assertThat(data.substring(10, 74)).isEqualTo(ChainRoleCheck.ANCHOR_ROLE.substring(2));
        assertThat(data.substring(74)).isEqualTo("0".repeat(24) + account.substring(2).toLowerCase());
    }

    @Test
    @DisplayName("키가 없으면 아무것도 안 한다 — 체인에 붙지도 않는다")
    void 키가_없으면_건너뛴다() {
        assertThatCode(checkWith(null)::check).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RPC 가 죽어 있어도 부팅을 막지 않는다 — WARN 한 줄로 끝난다")
    void 체인이_죽어_있어도_부팅을_막지_않는다() {
        assertThatCode(checkWith(KEY)::check).doesNotThrowAnyException();
    }

    /** 아무도 안 듣는 포트. 연결이 즉시 거절된다. */
    private ChainRoleCheck checkWith(String key) {
        ChainProperties props =
                new ChainProperties(
                        31221L,
                        "ws://127.0.0.1:1",
                        new ChainProperties.Relayer(key),
                        new ChainProperties.Anchor("-", 60, new ChainProperties.Anchor.Retry(3, 10)),
                        new ChainProperties.Indexer("-", 0, 10_000));
        ChainConnection connection = new ChainConnection(props);
        return new ChainRoleCheck(
                new TxSender(props, connection),
                connection,
                new CommitAnchorProperties(ANCHOR),
                new PredictTokenProperties(TOKEN));
    }
}
