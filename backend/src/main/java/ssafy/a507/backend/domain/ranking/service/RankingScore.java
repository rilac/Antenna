package ssafy.a507.backend.domain.ranking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 신뢰도 점수 산식 (ANT-RANK-01).
 *
 * <p>지라 AC 가 준 것은 골격뿐이다 — {@code (적중률×0.7 + 목표가 정확도×0.3) × 표본 가중치}.
 * 목표가 정확도를 오차에서 어떻게 만들지와 표본 가중치의 기준값은 정해져 있지 않아 여기서 정했다.
 * <b>숫자를 상수로 뽑아 둔 이유가 그것이다</b> — 팀이 다시 정하면 이 파일의 상수만 고친다.
 *
 * <p>구독자 수는 넣지 않는다(ERD 주석) — 인기투표로 회귀한다.
 */
final class RankingScore {

    /** AC 가 준 비율. */
    private static final BigDecimal HIT_WEIGHT = new BigDecimal("0.7");

    private static final BigDecimal ACCURACY_WEIGHT = new BigDecimal("0.3");

    /**
     * 목표가 정확도가 0 이 되는 오차(%). 오차 0% 면 만점, 10% 면 0 점이고 그 사이는 직선이다.
     * 10% 로 잡은 근거 — 예측 지평이 7~30일이라 그 구간 종가가 목표가에서 10% 이상 벗어나면
     * 방향은 맞아도 가격 예측으로는 의미가 없다고 봤다.
     */
    private static final BigDecimal ERROR_ZERO_POINT = new BigDecimal("10");

    /**
     * 표본 가중치가 만점이 되는 판정 건수. 3건 찍어 전부 맞힌 사람이 100건 중 70건 맞힌 사람을
     * 앞지르는 것을 막는 장치다. 20 으로 잡은 근거 — 시연 기간에 한 사람이 쌓을 수 있는 판정 수의
     * 현실적 상한 근처다.
     */
    private static final int SAMPLE_FULL_COUNT = 20;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private RankingScore() {}

    /**
     * 0~100 점. {@code rankings.score} 가 {@code numeric(8,3)} 이라 소수 셋째 자리까지 남긴다.
     *
     * @param avgError 평균 오차 %. 표본이 있어도 NULL 일 수 있다(오차를 못 낸 판정) — 그때는
     *     목표가 정확도를 0 으로 본다. 지어내지 않는다.
     */
    static BigDecimal of(int doneCount, int hitCount, BigDecimal avgError) {
        BigDecimal hitRate = ratio(hitCount, doneCount);
        BigDecimal accuracy = accuracy(avgError);
        BigDecimal weighted = hitRate.multiply(HIT_WEIGHT).add(accuracy.multiply(ACCURACY_WEIGHT));
        return weighted.multiply(sampleWeight(doneCount))
                .multiply(HUNDRED)
                .setScale(3, RoundingMode.HALF_UP);
    }

    /** 적중률 %. 화면과 {@code rankings.hit_rate} 가 쓰는 값이라 0~100 스케일이다. */
    static BigDecimal hitRatePercent(int doneCount, int hitCount) {
        return ratio(hitCount, doneCount).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal accuracy(BigDecimal avgError) {
        if (avgError == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal ratio = avgError.divide(ERROR_ZERO_POINT, 6, RoundingMode.HALF_UP);
        return BigDecimal.ONE.subtract(ratio).max(BigDecimal.ZERO);
    }

    private static BigDecimal sampleWeight(int doneCount) {
        return ratio(doneCount, SAMPLE_FULL_COUNT).min(BigDecimal.ONE);
    }

    private static BigDecimal ratio(int part, int whole) {
        if (whole <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), 6, RoundingMode.HALF_UP);
    }
}
