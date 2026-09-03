package ssafy.a507.backend.domain.research.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.research.entity.AiBriefing;

public interface AiBriefingRepository extends JpaRepository<AiBriefing, Long> {

    /**
     * 한 대상의 어느 날짜 브리핑. 배치가 이미 만든 행인지 볼 때 쓴다 — MARKET 은 {@code stockCode}
     * 가 null 이다.
     *
     * <p>Optional 이 아니라 List 다. MARKET 은 UQ 가 못 잡아(stock_code NULL) 두 JVM 이 같은 밤에
     * 돌면 행이 둘 생길 수 있는데, 그때 단건 조회는 예외로 죽고 그 날짜는 영영 다시 못 쓴다.
     * 호출 쪽이 첫 행을 집어 제자리에서 다시 쓴다. id 오름차순이라 첫 행이 가장 먼저 만든 것이다.
     */
    @Query("""
            select b from AiBriefing b
            where b.scope = :scope
              and (:stockCode is null and b.stock is null or b.stock.code = :stockCode)
              and b.targetDate = :targetDate
            order by b.id asc
            """)
    List<AiBriefing> findTarget(
            @Param("scope") AiBriefing.Scope scope,
            @Param("stockCode") String stockCode,
            @Param("targetDate") LocalDate targetDate);

    /**
     * 목록 조회(GET /briefings). 필터가 전부 선택이라 null 은 "가리지 않는다"다.
     *
     * <p>{@code date} 가 없으면 필터 안에서 가장 최신 영업일 한 날치만 내려준다 — 명세의 "기본
     * 최신". 그날짜를 먼저 {@link #findLatestTargetDate} 로 구해 넘긴다.
     */
    @Query("""
            select b from AiBriefing b
              left join fetch b.stock
            where (:scope is null or b.scope = :scope)
              and (:stockCode is null or b.stock.code = :stockCode)
              and b.targetDate = :targetDate
            order by b.scope asc, b.id asc
            """)
    List<AiBriefing> findAllOn(
            @Param("scope") AiBriefing.Scope scope,
            @Param("stockCode") String stockCode,
            @Param("targetDate") LocalDate targetDate);

    @Query("""
            select max(b.targetDate) from AiBriefing b
            where (:scope is null or b.scope = :scope)
              and (:stockCode is null or b.stock.code = :stockCode)
            """)
    Optional<LocalDate> findLatestTargetDate(
            @Param("scope") AiBriefing.Scope scope, @Param("stockCode") String stockCode);
}
