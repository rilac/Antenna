package ssafy.a507.backend.domain.research.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;

public interface ResearchPointRepository extends JpaRepository<ResearchPoint, Long> {

    /**
     * 그 종목·그 기준일 포인트를 이미 만들었는지 (배치 B6 의 멱등 키).
     *
     * <p>UQ 로 못 막는다 — {@code document_id} 가 nullable 이라 Postgres UQ 는 NULL 행을 서로 다른
     * 값으로 보고, {@code body} 는 회차마다 문장이 달라 키가 되지 못한다. 종목·날짜 단위 존재
     * 검사로 막는다. 주말 회차가 금요일 포인트를 다시 만들어 한도를 태우지 않게 하는 것이 목적이다.
     *
     * <p><b>배치 인스턴스가 하나라는 전제다.</b> 검사와 저장이 한 트랜잭션이 아니라, 두 인스턴스가
     * 같은 밤에 돌면 둘 다 통과해 종목당 카드가 두 벌 쌓인다. 포인트는 예측 근거 FK 때문에 삭제
     * 경로가 없어 사후 정리도 못 한다. 스케일아웃하려면 그때 배치 락(예: Redis)을 먼저 둔다.
     */
    boolean existsByStock_CodeAndTargetDate(String stockCode, LocalDate targetDate);

    /** {@code date} 없이 들어온 조회의 기본값 — 그 종목 포인트가 있는 가장 최근 영업일. */
    @Query("select max(p.targetDate) from ResearchPoint p where p.stock.code = :stockCode")
    Optional<LocalDate> findLatestTargetDate(@Param("stockCode") String stockCode);

    /**
     * 한 종목·한 날짜의 3열 전체.
     *
     * <p>문서를 함께 읽어 온다 — 응답의 출처 태그가 {@code document.source} 파생이라 지연 로딩으로
     * 두면 포인트 수만큼 쿼리가 붙는다.
     */
    @Query("""
            select p from ResearchPoint p
              left join fetch p.document
            where p.stock.code = :stockCode
              and p.targetDate = :targetDate
            order by p.id asc
            """)
    List<ResearchPoint> findOn(
            @Param("stockCode") String stockCode, @Param("targetDate") LocalDate targetDate);
}
