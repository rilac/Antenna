package ssafy.a507.backend.domain.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static ssafy.a507.backend.domain.prediction.entity.Prediction.Direction.DOWN;
import static ssafy.a507.backend.domain.prediction.entity.Prediction.Direction.UP;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

/**
 * ANT-PRED-04 — 판정 규칙. 스프링을 띄우지 않는다.
 *
 * <p>이 규칙이 앞으로 가장 자주 바뀔 자리라(리워드 차등·레이팅 ANT-RANK-07) 테스트도 가볍게 돌아야 한다.
 */
@DisplayName("판정 규칙")
class SettlementRulesTest {

    private static BigDecimal won(String v) {
        return new BigDecimal(v);
    }

    @Nested
    @DisplayName("HIT/MISS 는 기준가 대비 방향으로 가른다")
    class 방향 {

        @Test
        @DisplayName("UP 은 종가가 기준가보다 높으면 적중")
        void up_상승() {
            assertThat(SettlementRules.isHit(UP, won("80000"), won("85000"))).isTrue();
            assertThat(SettlementRules.isHit(UP, won("80000"), won("79999"))).isFalse();
        }

        @Test
        @DisplayName("DOWN 은 종가가 기준가보다 낮으면 적중")
        void down_하락() {
            assertThat(SettlementRules.isHit(DOWN, won("80000"), won("75000"))).isTrue();
            assertThat(SettlementRules.isHit(DOWN, won("80000"), won("80001"))).isFalse();
        }

        @Test
        @DisplayName("보합(기준가 == 종가)은 UP·DOWN 둘 다 적중 — 방향은 틀리지 않았고 오차가 차등을 만든다")
        void 보합은_양쪽_적중() {
            assertThat(SettlementRules.isHit(UP, won("80000"), won("80000"))).isTrue();
            assertThat(SettlementRules.isHit(DOWN, won("80000"), won("80000"))).isTrue();
        }

        @Test
        @DisplayName("scale 이 달라도 같은 값은 보합으로 본다 — numeric(14,2) 와 드라이버가 돌려주는 scale 이 어긋나도 판정이 흔들리면 안 된다")
        void scale_무시() {
            assertThat(SettlementRules.isHit(UP, won("80000.00"), won("80000.0"))).isTrue();
            assertThat(SettlementRules.isHit(DOWN, won("80000"), won("80000.000"))).isTrue();
        }

        @Test
        @DisplayName("판정 기준은 기준가다 — 목표가에 못 미쳐도 방향만 맞으면 적중")
        void 목표가_미달이어도_적중() {
            // 목표 82,000 을 못 갔지만 기준가 80,000 보다는 올랐다
            assertThat(SettlementRules.judge(UP, won("82000"), won("80000"), won("80500")).hit())
                    .isTrue();
        }

        @Test
        @DisplayName("기준가가 이미 목표가를 넘긴 채 시작해도 판정은 기준가 기준이다 — 갭 케이스를 사후 손질하지 않는다")
        void 갭_케이스() {
            // ref_close 79,500 · 목표 82,000 인데 기준가가 83,000 으로 확정된 건
            assertThat(SettlementRules.judge(UP, won("82000"), won("83000"), won("84000")).hit())
                    .isTrue();
            assertThat(SettlementRules.judge(UP, won("82000"), won("83000"), won("82500")).hit())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("error_rate 는 목표가 대비 근접도를 잰다")
    class 오차 {

        @Test
        @DisplayName("|종가 − 목표가| / 목표가 × 100, 소수 셋째 자리")
        void 산식() {
            // |85000 - 82000| / 82000 * 100 = 3.6585…
            assertThat(SettlementRules.errorRate(won("82000"), won("85000"))).isEqualByComparingTo("3.659");
        }

        @Test
        @DisplayName("목표가에 정확히 닿으면 0")
        void 정확히_달성() {
            assertThat(SettlementRules.errorRate(won("82000"), won("82000"))).isEqualByComparingTo("0.000");
        }

        @Test
        @DisplayName("부호가 없다 — 밑돌든 웃돌든 같은 거리면 같은 값")
        void 절대값() {
            assertThat(SettlementRules.errorRate(won("82000"), won("83000")))
                    .isEqualByComparingTo(SettlementRules.errorRate(won("82000"), won("81000")));
        }

        @Test
        @DisplayName("크게 초과 달성해도 오차로 센다 — 방향은 맞혔지만 가격은 근접하지 않았다")
        void 초과_달성도_오차() {
            // 적중이면서 오차 21.951% — 랭킹의 목표가 정확도 max(0, 1 − 오차/10) 은 0 이 된다. 의도한 동작이다.
            SettlementRules.Verdict verdict = SettlementRules.judge(UP, won("82000"), won("80000"), won("100000"));
            assertThat(verdict.hit()).isTrue();
            assertThat(verdict.errorRate()).isEqualByComparingTo("21.951");
        }

        @Test
        @DisplayName("항상 소수 셋째 자리로 떨어진다 — numeric(6,3) 에 그대로 들어가야 한다")
        void scale_고정() {
            assertThat(SettlementRules.errorRate(won("82000"), won("82000")).scale()).isEqualTo(3);
            assertThat(SettlementRules.errorRate(won("100"), won("133.33")).scale()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("judge 는 적중 여부와 오차를 같이 돌려준다 — 두 값이 한 규칙에서 나온다")
    void judge_묶음() {
        SettlementRules.Verdict verdict =
                SettlementRules.judge(Prediction.Direction.DOWN, won("70000"), won("80000"), won("72000"));
        assertThat(verdict.hit()).isTrue();
        // |72000 - 70000| / 70000 * 100 = 2.857…
        assertThat(verdict.errorRate()).isEqualByComparingTo("2.857");
    }
}
