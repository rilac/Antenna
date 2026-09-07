package ssafy.a507.backend.domain.ranking.service;

import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.common.Track;

/**
 * {@code rankings.filter_key} 를 만드는 유일한 곳 (ANT-RANK-02).
 *
 * <p>ERD 는 이 컬럼을 {@code varchar(30) 기간·섹터·시즌 필터 조합 키} 라고만 두고 값의 규칙을 적지 않았다.
 * 규칙을 여기 한 곳에 두는 이유는 랭킹 스냅샷 배치(ANT-RANK-01)가 <b>같은 함수로 키를 써야</b> 하기 때문이다.
 * 쓰는 쪽과 읽는 쪽이 각자 문자열을 조립하면 조회가 조용히 빈 목록이 된다.
 *
 * <table>
 *   <tr><td>REAL</td><td>{@code ALL} · {@code 30D} · {@code ALL:SEC:{업종명}} · {@code 30D:SEC:{업종명}}</td></tr>
 *   <tr><td>REPLAY</td><td>{@code ALL} · {@code SEASON:{시즌id}}</td></tr>
 * </table>
 *
 * <p>업종명은 KRX 업종명(`전기전자` 급)이라 접두사 8자를 더해도 30자 안에 들어간다. 그래도 넘치는 값이 오면
 * 예외 대신 조회 결과가 비어 나온다 — 읽기 경로라 그편이 안전하다. 길이 검사는 키를 <i>저장</i>하는
 * ANT-RANK-01 의 몫이다.
 */
public final class RankingFilterKey {

    private static final String ALL = "ALL";
    private static final String LAST_30_DAYS = "30D";
    private static final String SECTOR_INFIX = ":SEC:";
    private static final String SEASON_PREFIX = "SEASON:";

    private RankingFilterKey() {}

    /**
     * 트랙에 맞지 않는 파라미터는 400 이 아니라 무시한다 — REPLAY 에 준 period·sector, REAL 에 준 seasonId.
     * 명세 §종목 탐색이 무시하는 파라미터를 400 으로 만들지 않는 것과 같은 관례다.
     */
    public static String of(Track track, String period, String sector, Long seasonId) {
        if (track == Track.REPLAY) {
            return seasonId == null ? ALL : SEASON_PREFIX + seasonId;
        }
        String base = period(period);
        return isBlank(sector) ? base : base + SECTOR_INFIX + sector.trim();
    }

    /**
     * period 어휘. <b>프론트가 실제로 보내는 값이 기준이다</b> — {@code api/rankings.ts} 의
     * {@code PERIODS = ['ALL', 'D30']} 이고, "전체 · 최근 30일" 은 화면에 그리는 라벨일 뿐이다.
     * 명세서 표가 라벨("전체 | 30일")을 값처럼 적어 둬서 한글도 함께 받는다.
     *
     * <p>{@code D30} 을 안 받으면 랭킹 화면(E-01)의 "최근 30일" 탭이 통째로 400 이다.
     */
    private static String period(String period) {
        if (isBlank(period)) {
            return ALL;
        }
        return switch (period.trim()) {
            case "ALL", "전체" -> ALL;
            case "D30", "30D", "30일" -> LAST_30_DAYS;
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "period");
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
