package ssafy.a507.backend.domain.ranking.repository;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.common.Track;
import ssafy.a507.backend.domain.ranking.entity.Ranking;

public interface RankingRepository extends JpaRepository<Ranking, Long> {

    /**
     * 랭킹 한 장(ANT-RANK-02). 커서와 fromRank 가 같은 자리를 가리키므로 조회는 이것 하나다 —
     * 순위가 필터 조합마다 1부터 연속이라 "몇 등부터" 가 곧 오프셋이고, 스냅샷이라 그 사이에 행이 끼지 않는다.
     *
     * <p>{@code join fetch} 로 회원을 같이 끌어온다. 줄마다 nickname 을 실어야 해서 없으면 N+1 이다.
     *
     * <p>동점 회원 사이의 순서를 user.id 로 고정한다. 정렬 키가 rank 하나뿐이면 같은 rank 를 가진 두 줄의
     * 앞뒤가 호출마다 달라지고, 그 경계에서 페이지를 끊으면 한 회원이 두 장에 나오거나 어디에도 안 나온다.
     *
     * <p>ponytail: 순위 자체가 겹치지 않는다는 보장은 <b>쓰는 쪽</b>이 진다 — 랭킹 스냅샷 배치(ANT-RANK-01)는
     * 필터 조합마다 1부터 빈틈·중복 없는 rank 를 써야 한다(SQL 로는 {@code RANK()} 가 아니라
     * {@code ROW_NUMBER()}). 응답에 nextCursor 가 없어 프론트가 "받은 마지막 rank + 1" 로 다음 장을 부르기
     * 때문이다. 배치가 동점에 같은 rank 를 주면 그 등수의 뒷줄이 페이지 경계에서 통째로 건너뛰어진다.
     * 읽는 쪽에서 막으려면 경계의 동점 묶음을 통째로 실어야 하는데, 그건 이 API 가 질 복잡도가 아니다.
     */
    @Query("""
            select r from Ranking r
             join fetch r.user u
             where r.track = :track
               and r.filterKey = :filterKey
               and r.rank >= :fromRank
             order by r.rank asc, u.id asc
            """)
    List<Ranking> findPage(
            @Param("track") Track track,
            @Param("filterKey") String filterKey,
            @Param("fromRank") int fromRank,
            Limit limit);
}
