package ssafy.a507.backend.domain.monetize.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 광고 게재 조건 (ANT-COMMUNITY-05). 경매가 아니라 고정 단가 × 기간제 선착순이다.
 *
 * <p>단가는 여기 없다 — 토큰 금액표 {@code app.token.amounts.ad-per-day}({@code TokenAmountProperties}, ANT-TOKEN-08)에 있다.
 * 금액을 도메인마다 따로 두면 슬롯 초과·광고·보너스가 서로 말이 되는지 한눈에 볼 수 없다(광고가 1 ANT = 1원으로 사실상 공짜였던 걸
 * 아무도 못 봤다).
 *
 * @param slotCount 같은 기간에 받을 수 있는 배너 수. 메인 배너 자리가 하나라 1 이다 —
 *     자리를 늘리면 이 값만 바꾼다.
 * @param maxDays 한 번에 살 수 있는 최대 기간. 상한이 없으면 한 명이 몇 년치를 선점한다.
 * @param pendingGraceMinutes 확정을 기다리는 신청이 자리를 잡고 있는 시간. 접수는 토큰을
 *     차감하지 않으므로, 이 창이 없으면 신청만 해 두고 소각 tx 를 보내지 않는 계정이 공짜로
 *     자리를 막는다. 온체인 확정에 걸리는 시간보다 넉넉하되 짧게 잡는다.
 */
@ConfigurationProperties(prefix = "app.ads")
public record AdProperties(
        Integer slotCount,
        Integer maxDays,
        Integer pendingGraceMinutes) {

    public AdProperties {
        slotCount = slotCount == null ? 1 : slotCount;
        maxDays = maxDays == null ? 30 : maxDays;
        pendingGraceMinutes = pendingGraceMinutes == null ? 10 : pendingGraceMinutes;
    }
}
