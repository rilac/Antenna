package ssafy.a507.backend.domain.account.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.account.entity.WatchlistItem;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    /**
     * 내 관심 종목 전부, 최근 담은 순. 종목을 함께 읽는다 — 행마다 이름을 따로 읽으면 N+1 이다.
     * 같은 순간에 담긴 둘은 id 로 순서를 고정해 새로고침마다 자리가 바뀌지 않게 한다.
     */
    @Query(
            """
            select w from WatchlistItem w join fetch w.stock
            where w.user.id = :userId
            order by w.createdAt desc, w.id desc
            """)
    List<WatchlistItem> findAllWithStock(@Param("userId") Long userId);

    boolean existsByUser_IdAndStock_Code(Long userId, String stockCode);

    /** 내가 담은 종목코드 전부. 탐색 목록이 행마다 exists 를 묻지 않고 한 번에 받는다. */
    @Query("select w.stock.code from WatchlistItem w where w.user.id = :userId")
    List<String> findWatchedCodes(@Param("userId") Long userId);

    /** 빼기. 엔티티를 읽어 지우면 select 가 한 번 더 나가므로 한 문장으로 지운다. 없으면 0 — 오류가 아니다. */
    @Modifying
    @Query("delete from WatchlistItem w where w.user.id = :userId and w.stock.code = :stockCode")
    int deleteByUserIdAndStockCode(@Param("userId") Long userId, @Param("stockCode") String stockCode);
}
