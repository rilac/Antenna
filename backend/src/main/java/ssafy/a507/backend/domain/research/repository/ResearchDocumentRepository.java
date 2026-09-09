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
