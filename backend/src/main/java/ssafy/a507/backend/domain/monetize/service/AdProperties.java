package ssafy.a507.backend.domain.monetize.service;

import java.math.BigInteger;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 광고 게재 조건 (ANT-COMMUNITY-05). 경매가 아니라 고정 단가 × 기간제 선착순이라 값이 셋뿐이다.
 *
 * @param pricePerDayWei 하루치 게재료. 금액표(D4)가 아직 열려 있어 코드에 못 박지 않는다.
 *     <b>단위는 정수 ANT 다</b> — ANT 는 decimals 0 이고(ANT-CHAIN-03) 원장·소각이 모두 정수로
 *     돌아간다. 이름의 wei 는 금액 필드 형식 통일에서 온 것이고 10^18 을 곱한 값이 아니다.
 * @param slotCount 같은 기간에 받을 수 있는 배너 수. 메인 배너 자리가 하나라 1 이다 —
 *     자리를 늘리면 이 값만 바꾼다.
 * @param maxDays 한 번에 살 수 있는 최대 기간. 상한이 없으면 한 명이 몇 년치를 선점한다.
 * @param pendingGraceMinutes 확정을 기다리는 신청이 자리를 잡고 있는 시간. 접수는 토큰을
 *     차감하지 않으므로, 이 창이 없으면 신청만 해 두고 소각 tx 를 보내지 않는 계정이 공짜로
 *     자리를 막는다. 온체인 확정에 걸리는 시간보다 넉넉하되 짧게 잡는다.
 */
@ConfigurationProperties(prefix = "app.ads")
public record AdProperties(
        BigInteger pricePerDayWei,
        Integer slotCount,
        Integer maxDays,
        Integer pendingGraceMinutes) {

    public AdProperties {
        // 1 ANT/일. 설정을 넣지 않은 팀원의 로컬에서도 값이 서게 두려는 기본값이다.
        pricePerDayWei = pricePerDayWei == null ? BigInteger.ONE : pricePerDayWei;
        slotCount = slotCount == null ? 1 : slotCount;
        maxDays = maxDays == null ? 30 : maxDays;
        pendingGraceMinutes = pendingGraceMinutes == null ? 10 : pendingGraceMinutes;
    }

    public BigInteger priceFor(int days) {
        return pricePerDayWei.multiply(BigInteger.valueOf(days));
    }
}
