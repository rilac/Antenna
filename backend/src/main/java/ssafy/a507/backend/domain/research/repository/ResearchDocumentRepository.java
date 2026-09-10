package ssafy.a507.backend.domain.research.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;

public interface ResearchDocumentRepository extends JpaRepository<ResearchDocument, Long> {

    /** 이번 회차에 받은 것 중 이미 있는 건을 한 번에 걸러낸다 — 건별 조회는 왕복이 너무 잦다. */
    List<ResearchDocument> findAllBySourceAndExternalIdIn(
            ResearchDocument.Source source, List<String> externalIds);

    /**
     * 브리핑 재료 — 기준일 안에 나온 뉴스 중 최신 몇 건 (ANT-RESEARCH-03).
     *
     * <p>기준일로 묶는 이유: 브리핑 기준일은 일봉이 들어온 마지막 영업일(D-1)인데 뉴스는 그날 저녁
     * 것(D)까지 들어와 있다. 묶지 않으면 D-1 시세에 D 기사가 붙어 "3% 급등" 요약과 "-0.8%" 수치가
     * 한 프롬프트에 들어간다.
     */
    List<ResearchDocument> findByStock_CodeAndSourceAndPublishedAtBeforeOrderByPublishedAtDesc(
            String stockCode, ResearchDocument.Source source, Instant before, Limit limit);

    /**
     * 아직 관련도 판정이 없는 기사 (ANT-RESEARCH-02).
     *
     * <p>{@code promptVersion} 이 조건에 들어간다 — 지시문을 고쳐 세대를 올리면 옛 세대 행이
     * 다시 후보가 된다. 판정이 아예 없는 기사와 옛 세대 기사를 한 질의로 집는다.
     *
     * <p><b>종목을 함께 읽어 온다.</b> 판정 프롬프트에 회사 이름이 들어가는데, 배치는 트랜잭션 없이
     * 돌고 {@code spring.jpa.open-in-view} 가 false 라 지연 로딩이 열리지 않는다. 없으면 첫 기사에서
     * LazyInitializationException 으로 회차가 통째로 죽는다. {@code left} 인 이유는 종목이 NULL 인
     * 시장 전체 기사도 대상이라서다 — inner 로 두면 그 기사들이 조용히 빠진다.
     */
    @Query("""
            select d from ResearchDocument d
              left join fetch d.stock
            where d.source = :source
              and d.publishedAt >= :since
              and not exists (
                    select 1 from NewsSignal s
                    where s.document = d and s.promptVersion = :promptVersion)
            order by d.id
            """)
    List<ResearchDocument> findUnjudged(
            @Param("source") ResearchDocument.Source source,
            @Param("since") Instant since,
            @Param("promptVersion") String promptVersion,
            Limit limit);

    /**
     * 종목별 문서 한 페이지. 최신순 고정이라 커서는 id 하나다.
     *
     * <p>{@code source} 가 null 이면 원천을 가리지 않는다 — 화면이 탭 없이 한 줄로 섞어
     * 보여 주는 경우가 기본값이다.
     */
    @Query("""
            select d from ResearchDocument d
            where d.stock.code = :stockCode
              and (:source is null or d.source = :source)
              and (:cursorId is null or d.id < :cursorId)
            order by d.id desc
            """)
    List<ResearchDocument> findPageByStock(
            @Param("stockCode") String stockCode,
            @Param("source") ResearchDocument.Source source,
            @Param("cursorId") Long cursorId,
            Limit limit);
}
