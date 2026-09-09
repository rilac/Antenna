package ssafy.a507.backend.domain.prediction.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.prediction.entity.Prediction;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    /**
     * 내 예측 목록(ANT-PRED-06) — 최신부터, 커서 id 미만. 정렬 키를 created_at 이 아니라 id 로 두는 이유는
     * 같은 초에 봉인한 두 건이 커서 페이징에서 겹치거나 빠지지 않게 하기 위해서다(id 는 등록 순서와 같다).
     *
     * <p>{@code join fetch} 로 종목을 같이 끌어온다 — 항목마다 stockName 을 실어야 해서 없으면 N+1 이다.
     */
    @Query("""
            select p from Prediction p
             left join fetch p.stock
             where p.user.id = :userId
               and p.status in :statuses
               and (:cursorId is null or p.id < :cursorId)
             order by p.id desc
            """)
    List<Prediction> findMyPage(
            @Param("userId") Long userId,
            @Param("statuses") Collection<Prediction.Status> statuses,
            @Param("cursorId") Long cursorId,
            Limit limit);

    /** 슬롯 계산(ANT-PRED-01) — KST 하루 구간 [from, to) 에 이 사용자가 등록한 수. 트랙 구분 없이 센다(예측은 REAL 전용). */
    long countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, Instant from, Instant to);

    /** 상태별 내 예측 수. 목록 위 요약 칩(total·pending·judged·hitRate)의 재료다. */
    @Query("""
            select p.status, count(p) from Prediction p
             where p.user.id = :userId
             group by p.status
            """)
    List<Object[]> countMineByStatus(@Param("userId") Long userId);
}
