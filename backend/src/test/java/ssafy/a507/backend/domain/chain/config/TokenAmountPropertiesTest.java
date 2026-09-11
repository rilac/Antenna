package ssafy.a507.backend.domain.chain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ANT-TOKEN-08 — 금액표는 코드 기본값이 없다. 빠지거나 0 이하면 바인딩(= 부팅)에서 멈춰야 한다.
 * yaml → 레코드 바인딩 자체는 모든 {@code @SpringBootTest} 가 테스트 yaml 로 매번 검증한다.
 */
@DisplayName("토큰 금액표(TokenAmountProperties)")
class TokenAmountPropertiesTest {

    private static final BigInteger BONUS = BigInteger.valueOf(10_000);
    private static final BigInteger THOUSAND = BigInteger.valueOf(1_000);

    @Test
    @DisplayName("값이 없으면 키 이름을 말하며 멈춘다 — 틀린 기본값으로 조용히 돌지 않는다")
    void 값_누락() {
        assertThatThrownBy(() -> new TokenAmountProperties(BONUS, null, THOUSAND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.token.amounts.slot-over");
    }

    @Test
    @DisplayName("0 이하면 멈춘다 — 공짜 소각·음수 mint 를 설정 실수로 만들지 않는다")
    void 영_이하() {
        assertThatThrownBy(() -> new TokenAmountProperties(BONUS, THOUSAND, BigInteger.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ad-per-day");
        assertThatThrownBy(() -> new TokenAmountProperties(BigInteger.valueOf(-1), THOUSAND, THOUSAND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signup-bonus");
    }

    @Test
    @DisplayName("광고 게재료 = 하루치 × 일수")
    void 광고_게재료() {
        assertThat(new TokenAmountProperties(BONUS, THOUSAND, THOUSAND).adFor(7)).isEqualTo(BigInteger.valueOf(7_000));
    }
}
