package ssafy.a507.backend.domain.prediction.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

/**
 * 판정 규칙 (ANT-PRED-04, plan §설계 ④·⑤). 스프링이 없는 순수 함수만 둔다.
 *
 * <p><b>왜 한 파일에 가두는가.</b> 앞으로 점수 체계가 바뀔 가능성이 높다 — 목표가 근접도에 따른 리워드 차등,
 * 여론 역행 가중치, 레이팅제(ANT-RANK-07) 가 논의 중이다. 규칙이 배치 루프·트랜잭션·쿼리에 스며들면 그때 갈아엎게 된다.
 * 여기 두면 고칠 파일이 하나다.
 *
 * <p><b>적중과 정확도는 다른 축이다.</b> HIT/MISS 는 방향만 본다. "얼마나 근접했나" 는 {@code errorRate} 가 따로 잰다.
 * 둘을 한 값에 섞으면 랭킹이 같은 것을 두 번 재게 된다(적중률 0.7 + 목표가 정확도 0.3).
 */
public final class SettlementRules {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 나눗셈 중간 자리수. 최종 scale(3) 보다 넉넉히 둬야 반올림이 한 번만 일어난다. */
    private static final int DIVIDE_SCALE = 8;

    private SettlementRules() {}

    /** 판정 한 건. 규칙이 두 값을 같이 정하므로 같이 돌려준다. */
    public record Verdict(boolean hit, BigDecimal errorRate) {}

    public static Verdict judge(
            Prediction.Direction direction,
            BigDecimal targetPrice,
            BigDecimal basePrice,
            BigDecimal settlePrice) {
        return new Verdict(isHit(direction, basePrice, settlePrice), errorRate(targetPrice, settlePrice));
    }

    /**
     * 방향 적중. 기준은 <b>기준가</b>지 등록 때 본 직전 종가(ref_close)가 아니다 — 기준가가 존재하는 이유가
     * "장중 정보 우위 제거"(ANT-PRED-03)라서, 등록 때 본 값으로 판정하면 그 취지가 사라진다.
     *
     * <p><b>보합(기준가 == 종가)은 HIT 이다.</b> 방향은 틀리지 않았고, 목표가에서 벌어진 만큼 오차가 알아서 깎는다.
     * MISS 로 두면 "방향 적중" 의 정의에 예외 한 줄이 붙고 그 예외가 화면 문구·통계·나중의 레이팅 델타까지 따라다닌다.
     * 정확히 같은 종가는 저유동성 종목에서 실제로 나오며, 그날 그 종목에 건 사람은 UP·DOWN 모두 HIT 이 된다.
     *
     * <p>{@code compareTo} 는 scale 을 무시한다 — 80000.00 과 80000.0 을 같게 본다. numeric(14,2) 와
     * H2·PostgreSQL 이 돌려주는 scale 이 어긋나도 판정이 흔들리지 않는다. {@code equals} 였다면 흔들린다.
     */
    static boolean isHit(Prediction.Direction direction, BigDecimal basePrice, BigDecimal settlePrice) {
        int cmp = settlePrice.compareTo(basePrice);
        return direction == Prediction.Direction.UP ? cmp >= 0 : cmp <= 0;
    }

    /**
     * 목표가 오차 % = |종가 − 목표가| / 목표가 × 100. 소수 셋째 자리 반올림(numeric(6,3)).
     *
     * <p><b>분모가 목표가인 이유</b>: 컬럼 이름이 "목표가 오차" 다. "목표가 대비 몇 % 빗나갔나" 라는 문장이
     * 성립하려면 분모가 목표가여야 한다. 기준가를 분모로 쓰면 이름과 값이 어긋난다.
     *
     * <p><b>초과 달성을 0 으로 클램프하지 않는 이유</b>: 이 값이 재는 것은 근접도다. 목표 82,000 이라 해 놓고
     * 100,000 에서 끝났으면 방향은 맞혔지만 가격은 근접하지 않았다. 클램프하면 방향을 맞힌 건이 전부 0 에 몰려
     * 랭킹의 두 입력(적중률·정확도)이 같은 것을 재게 된다.
     *
     * <p>0 나눗셈은 없다 — 등록이 {@code @DecimalMin("0.01")} 로 막는다.
     */
    static BigDecimal errorRate(BigDecimal targetPrice, BigDecimal settlePrice) {
        return settlePrice
                .subtract(targetPrice)
                .abs()
                .divide(targetPrice, DIVIDE_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(3, RoundingMode.HALF_UP);
    }
}
