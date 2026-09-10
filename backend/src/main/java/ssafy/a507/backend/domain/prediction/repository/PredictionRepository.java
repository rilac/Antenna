package ssafy.a507.backend.domain.prediction.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.common.Track;
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

    /**
     * 판정 배치(ANT-PRED-03·04)가 훑을 대상의 <b>id 만</b> 가져온다. 한 건이 트랜잭션 하나라 엔티티는 그 트랜잭션 안에서 다시 읽는다 —
     * 여기서 엔티티를 통째로 들고 나오면 루프 내내 준영속 상태로 붙들고 있게 된다.
     *
     * <p>날짜 조건이 없다. 대상 선별을 "시세가 들어왔나" 로 하기 때문이다(plan §설계 ②) — 날짜로 자르면 그날 종가가 아직 없는
     * 건이 다음 회차에 조건에서 빠져 영영 전이되지 않는다. 13:30 에 볼 수 있는 최신 종가는 어제 것이다.
     *
     * <p>{@code track} 을 거는 이유: REPLAY 는 게임일 진행이 전이시킨다. 실전 배치가 시즌 예측을 건드리면 안 된다.
     */
    @Query("select p.id from Prediction p where p.track = :track and p.status = :status order by p.id")
    List<Long> findIdsByTrackAndStatus(
            @Param("track") Track track, @Param("status") Prediction.Status status);

    /** 상태별 내 예측 수. 목록 위 요약 칩(total·pending·judged·hitRate)의 재료다. */
    @Query("""
            select p.status, count(p) from Prediction p
             where p.user.id = :userId
             group by p.status
            """)
    List<Object[]> countMineByStatus(@Param("userId") Long userId);
}
