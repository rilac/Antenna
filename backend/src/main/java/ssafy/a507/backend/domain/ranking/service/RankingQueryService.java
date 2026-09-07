package ssafy.a507.backend.domain.ranking.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.common.Track;
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
