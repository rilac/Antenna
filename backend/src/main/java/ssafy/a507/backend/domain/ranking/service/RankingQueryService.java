package ssafy.a507.backend.domain.ranking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.common.Track;
import ssafy.a507.backend.domain.ranking.dto.MyRankResponse;
import ssafy.a507.backend.domain.ranking.dto.RankingItemResponse;
import ssafy.a507.backend.domain.ranking.dto.RankingListResponse;
import ssafy.a507.backend.domain.ranking.entity.Ranking;
import ssafy.a507.backend.domain.ranking.repository.RankingRepository;

/**
 * 랭킹 조회 (ANT-RANK-02, 화면 E-01 · B-01).
 *
 * <p>여기서 계산하는 것은 없다. 순위·점수·적중률은 전부 배치(ANT-RANK-01)가 미리 써 둔 값이고,
 * 이 클래스는 필터를 키로 바꿔 한 장을 떠올 뿐이다. 요청 시점 재계산은 명세가 금지한다 —
 * 같은 시각에 두 사람이 본 순위가 달라지면 안 된다.
 *
 * <p>ponytail: Redis 캐시를 건너뛰고 rankings 를 직접 읽는다. 캐시를 채우는 쪽이 아직 없어서다(ANT-RANK-01).
 * 배치가 생기면 이 메서드 안에서만 캐시를 먼저 보게 바꾸면 되고, 컨트롤러·응답 계약은 그대로다.
 */
@Service
@RequiredArgsConstructor
public class RankingQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int FIRST_RANK = 1;

    private final RankingRepository rankings;

    @Transactional(readOnly = true)
    public RankingListResponse list(
            Track track,
            String period,
            String sector,
            Long seasonId,
            Integer limit,
            Integer cursor,
            Integer fromRank) {

        String filterKey = RankingFilterKey.of(track, period, sector, seasonId);
        List<Ranking> page =
                rankings.findPage(
                        track, filterKey, startRank(cursor, fromRank), Limit.of(pageSize(limit)));

        if (page.isEmpty()) {
            return RankingListResponse.empty();
        }
        // 한 필터의 행들은 같은 배치가 한꺼번에 쓴 것이라 산출 시각이 모두 같다.
        return new RankingListResponse(
                page.get(0).getComputedAt(), page.stream().map(RankingItemResponse::from).toList());
    }

    /**
     * 내 순위 (ANT-RANK-03, 화면 E-01 상단 카드). 랭킹에 안 들었으면 빈 값 — 컨트롤러가 204 로 바꾼다.
     *
     * <p>REAL 은 기간·섹터를 걸지 않은 전체 기준({@code ALL}) 한 줄만 본다. 명세의 파라미터가
     * track·seasonId 뿐이고, 화면도 목록의 필터 탭과 무관하게 카드 하나를 고정으로 띄운다.
     */
    @Transactional(readOnly = true)
    public Optional<MyRankResponse> myRank(Long userId, Track track, Long seasonId) {
        String filterKey = RankingFilterKey.of(track, null, null, seasonId);
        Optional<Ranking> mine =
                rankings.findByTrackAndFilterKeyAndUserId(track, filterKey, userId);
        if (mine.isEmpty()) {
            return Optional.empty();
        }

        long total = rankings.countByTrackAndFilterKey(track, filterKey);
        if (total <= 0) {
            /* 내 행을 찾은 직후 그 필터가 비었다 — 배치(ANT-RANK-01)가 전량 재계산으로 지우는
               중이다. READ COMMITTED 라 두 쿼리가 서로 다른 스냅샷을 볼 수 있어 실제로 가능하다.
               백분위를 0 으로 내리면 화면이 "상위 0%" 로 그려 최상위처럼 보이므로, 값을 지어내지
               않고 랭킹에 없는 것과 같이 취급한다(204). 카드만 사라지고 다음 조회에서 복구된다. */
            return Optional.empty();
        }
        return Optional.of(new MyRankResponse(
                mine.get().getRank(),
                percentile(mine.get().getRank(), total),
                delta(mine.get()),
                tier()));
    }

    /**
     * 상위 몇 % — {@code rank / 전체 × 100}, 소수 첫째 자리. <b>작을수록 상위</b>다.
     *
     * <p>백분위의 통상 정의(아래에 몇 %가 있는가)와 방향이 반대다. 화면이 이 값을 그대로
     * "상위 3.7%" 로 그리기 때문이고, 프론트 {@code api/mock/rankings.ts} 도 같은 식을 쓴다.
     *
     * <p>{@code rank} 가 {@code total} 을 넘으면 100 을 넘는 값이 나온다. 막지 않는 이유는 그것이
     * 배치가 순위를 1..N 으로 촘촘히 쓰지 않았다는 신호이고, 그 불변식은 쓰는 쪽이 지켜야 하기
     * 때문이다(리포지토리 주석 참고). 여기서 100 으로 깎으면 배치 버그가 조용히 숨는다.
     */
    private BigDecimal percentile(int rank, long total) {
        return BigDecimal.valueOf(rank)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    /**
     * 직전 스냅샷 대비 순위 변동. <b>양수가 상승</b>이다 — 순위는 숫자가 작을수록 위라서
     * {@code prevRank - rank} 로 부호가 뒤집힌다(3등 → 1등이면 +2).
     *
     * <p>{@code prevRank} 가 null 이면 0 이다. 직전 회차에 이 필터에 없었다는 뜻이라 비교할
     * 대상이 없다 — 새로 진입했거나 첫 스냅샷이다. <b>응답에서 null 이 아니라 0 인 이유</b>는
     * 화면이 {@code delta !== 0} 으로 표시를 감추기 때문이다. null 을 주면 "▼ 0" 을 그린다.
     */
    private int delta(Ranking mine) {
        Integer previous = mine.getPrevRank();
        return previous == null ? 0 : previous - mine.getRank();
    }

    /**
     * 리플레이 전용 티어({@code DIAMOND·PLATINUM·GOLD}). 실전은 언제나 null 이다 —
     * 실전 프로필에 티어를 노출하지 않는다(설계 제약, 화면설계서 E-01).
     *
     * <p>ponytail: REPLAY 도 아직 null 이다. 티어 경계 수치가 서버 상수(ANT-TOKEN-08)로 미뤄져
     * 있고, 리플레이 예측(ANT-SEASON-04)이 없어 REPLAY 행 자체가 생기지 않는다. 경계가 정해지면
     * percentile 을 구간에 맞추면 된다. 화면은 {@code track === 'REPLAY' && me.tier} 로 걸러
     * null 이어도 안전하다.
     */
    private String tier() {
        return null;
    }

    /**
     * cursor 와 fromRank 는 둘 다 "몇 등부터" 다. 순위 직행(fromRank)이 더 구체적인 요청이라 우선한다.
     * 프론트는 다음 장을 받은 마지막 rank + 1 로 부른다.
     */
    private int startRank(Integer cursor, Integer fromRank) {
        Integer requested = fromRank != null ? fromRank : cursor;
        return requested == null || requested < FIRST_RANK ? FIRST_RANK : requested;
    }

    private int pageSize(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }
}
