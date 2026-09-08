package ssafy.a507.backend.domain.season.repository;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.season.entity.SeasonTrade;

public interface SeasonTradeRepository extends JpaRepository<SeasonTrade, Long> {
    /** 회차의 체결 전부, 오래된 순. 종료 시 결과 계산이 처음부터 다시 돌린다. */
    @EntityGraph(attributePaths = "ticker")
    List<SeasonTrade> findByParticipant_IdOrderByIdAsc(Long participantId);

    /**
     * 체결 내역 한 페이지. 최근 체결이 먼저라 커서는 {@code id <} 로 내려간다 — 목록 순서와
     * 커서 방향이 어긋나면 다음 페이지가 앞쪽을 다시 준다. 종목·방향 필터는 null 이면 전체다.
     */
    @Query("""
            select t from SeasonTrade t
              join fetch t.ticker
             where t.participant.id = :participantId
               and (:tickerId is null or t.ticker.id = :tickerId)
               and (:side is null or t.side = :side)
               and (:cursor is null or t.id < :cursor)
             order by t.id desc
            """)
    List<SeasonTrade> findPage(
            @Param("participantId") Long participantId,
            @Param("tickerId") Long tickerId,
            @Param("side") SeasonTrade.Side side,
            @Param("cursor") Long cursor,
            Limit limit);
}
