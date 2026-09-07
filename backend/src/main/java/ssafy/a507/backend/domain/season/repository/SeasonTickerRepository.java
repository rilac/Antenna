package ssafy.a507.backend.domain.season.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.season.entity.SeasonTicker;

public interface SeasonTickerRepository extends JpaRepository<SeasonTicker, Long> {

    /**
     * 시즌별 종목 수를 한 번에 센다. 목록의 행마다 count 를 돌리면 N+1 이다.
     *
     * <p>종목이 하나도 없는 시즌은 결과에 나오지 않는다 — 호출부가 0 으로 채운다.
     */
    @Query("select new ssafy.a507.backend.domain.season.repository.SeasonTickerCount("
            + "t.season.id, count(t)) from SeasonTicker t"
            + " where t.season.id in :seasonIds group by t.season.id")
    List<SeasonTickerCount> countBySeasonIdIn(@Param("seasonIds") Collection<Long> seasonIds);

    long countBySeason_Id(Long seasonId);

    /** 시즌 종목 목록. 가명 순이라 A사·B사·C사 순으로 온다. */
    List<SeasonTicker> findBySeason_IdOrderByDisplayNameAsc(Long seasonId);

    /**
     * 그 시즌의 그 종목. 시즌 id 를 조건에 함께 넣는다 — id 만으로 찾으면 남의 시즌
     * 종목 id 를 넣어 가격을 떠볼 수 있다.
     */
    Optional<SeasonTicker> findByIdAndSeason_Id(Long tickerId, Long seasonId);
}
