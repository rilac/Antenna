package ssafy.a507.backend.domain.prediction.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 기준일 계산 (ANT-PRED-01, plan §5-②).
 *
 * <p>"다음 영업일" 을 <b>다음 평일</b>로 근사한다. 미래의 휴장일은 시세 테이블에 없어 알 수 없고, 누가 계산해도 같은 값이
 * 나와야 한다(커밋·D-day 의 기준). 공휴일에 걸리면 그날 시세가 없을 뿐이고, 기준가 배치(PRED-03)는 "시세 없으면 보류" 라
 * 다음 거래일 종가를 기다린다. 기준가가 늦게 확정될 뿐 예측이 틀어지진 않는다.
 */
public final class BusinessDays {

    private BusinessDays() {}

    /** {@code from} 다음의 첫 평일(월~금). 금요일이면 월요일, 토·일이면 월요일. */
    public static LocalDate nextWeekday(LocalDate from) {
        LocalDate d = from.plusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }
}
