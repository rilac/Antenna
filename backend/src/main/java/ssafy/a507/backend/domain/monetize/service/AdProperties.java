package ssafy.a507.backend.domain.monetize.service;

import java.math.BigInteger;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 광고 게재 조건 (ANT-COMMUNITY-05). 경매가 아니라 고정 단가 × 기간제 선착순이라 값이 셋뿐이다.
 *
 * @param pricePerDayWei 하루치 게재료. 금액표(D4)가 아직 열려 있어 코드에 못 박지 않는다.
 * @param slotCount 같은 기간에 받을 수 있는 배너 수. 메인 배너 자리가 하나라 1 이다 —
 *     자리를 늘리면 이 값만 바꾼다.
 * @param maxDays 한 번에 살 수 있는 최대 기간. 상한이 없으면 한 명이 몇 년치를 선점한다.
 */
@ConfigurationProperties(prefix = "app.ads")
public record AdProperties(BigInteger pricePerDayWei, Integer slotCount, Integer maxDays) {

    public AdProperties {
        // 1 ANT/일. 설정을 넣지 않은 팀원의 로컬에서도 값이 서게 두려는 기본값이다.
        pricePerDayWei = pricePerDayWei == null ? BigInteger.TEN.pow(18) : pricePerDayWei;
        slotCount = slotCount == null ? 1 : slotCount;
        maxDays = maxDays == null ? 30 : maxDays;
    }

    public BigInteger priceFor(int days) {
        return pricePerDayWei.multiply(BigInteger.valueOf(days));
    }
}
