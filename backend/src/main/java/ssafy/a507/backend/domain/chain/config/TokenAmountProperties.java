package ssafy.a507.backend.domain.chain.config;

import java.math.BigInteger;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토큰 금액표 (ANT-TOKEN-08). ANT 가 들고 나는 금액은 전부 여기 한 곳에서 읽는다.
 *
 * <p><b>단위는 정수 ANT 다.</b> decimals 0 이고(ANT-CHAIN-03) 원화 스테이블코인을 붙일 것을 가정해 1 ANT ≈ 1원으로 잡는다.
 * 10^18 을 곱하지 않는다 — 이름에 {@code wei} 를 쓰지 않는 것도 그 오해를 막으려는 것이다(-220 · -225).
 *
 * <p><b>기본값을 두지 않는다.</b> 값은 {@code application.yaml} 한 곳에만 있고, 비었거나 0 이하면 부팅을 멈춘다.
 * 코드에 기본값이 있으면 설정을 빠뜨려도 틀린 금액으로 조용히 돈다 — 광고 단가가 10^18 기본값을 들고 있던 사고(-220)가
 * 그 모양이었다. 환경변수로도 받지 않는다: 금액은 환경이 아니라 정책이라 모든 환경이 같아야 하고, 바뀌면 git 에 남아야 한다.
 *
 * <p>여러 도메인(예측·광고·회원)이 읽으므로 쓰는 쪽 빈마다 {@code @EnableConfigurationProperties} 로 켠다
 * ({@code PublicDataProperties} 와 같은 방식). 시즌 참가비는 여기 없다 — 명세가 시즌마다 다른 {@code seasons} 컬럼으로 정했다.
 *
 * @param signupBonus 지갑 연동 시 가입 보너스 mint, 1인 1회 (TOKEN-07)
 * @param slotOver 하루 무료 슬롯을 넘긴 예측 1건의 소각액 (PRED-01)
 * @param adPerDay 메인 배너 하루치 게재료 소각액 (COMMUNITY-05). 고정 단가 × 기간제 — 경매형으로 바뀌면 이 값의 뜻이 바뀐다
 */
@ConfigurationProperties(prefix = "app.token.amounts")
public record TokenAmountProperties(BigInteger signupBonus, BigInteger slotOver, BigInteger adPerDay) {

    public TokenAmountProperties {
        requirePositive("signup-bonus", signupBonus);
        requirePositive("slot-over", slotOver);
        requirePositive("ad-per-day", adPerDay);
    }

    /** 광고 {@code days} 일치 게재료. 접수 시 {@code ad_banners.price_wei} 에 박제되므로 단가를 바꿔도 이미 받은 신청은 그대로다. */
    public BigInteger adFor(int days) {
        return adPerDay.multiply(BigInteger.valueOf(days));
    }

    private static void requirePositive(String key, BigInteger value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException(
                    "app.token.amounts." + key + " 가 없거나 0 이하다 — 금액은 application.yaml 에만 있고 코드 기본값이 없다");
        }
    }
}
