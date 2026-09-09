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
     * 요약이 필요한 문서 한 묶음 (배치 B6).
     *
     * <p>아직 요약이 없는 행과, 프롬프트 세대가 뒤처진 행을 한 조건으로 집는다 — 프롬프트를
     * 고치면 {@code app.ai.prompt-version} 만 올리면 되고 재생성 경로를 따로 만들지 않는다.
     *
     * <p>종목을 함께 읽어 온다. 요약 프롬프트에 종목명을 넣어야 어느 회사 이야기인지 모델이
     * 알고, 건마다 프록시를 깨우면 묶음 크기만큼 쿼리가 늘어난다.
     *
     * <p><b>{@code since} 로 기간을 자르는 이유 (2026-09-09).</b> 이 조건이 없으면 "요약이 없는
     * 행"이 영원히 대상으로 남는다. 요약 배치를 꺼 두는 동안에도 수집은 계속 돌아 하루 ~900건씩
     * 쌓이고, 다시 켜는 날 밀린 것부터 회차당 상한만큼 며칠에 걸쳐 그대로 GMS 에 청구된다 —
     * 끄는 것으로 비용이 없어지는 게 아니라 이연될 뿐이었다. 기간을 자르면 재개한 시점부터의
     * 기사만 요약하고 그 사이 행은 {@code summary} 가 NULL 인 채로 남는다. 지난 기사 요약은
     * 화면에서 값이 거의 없어 그렇게 두기로 했다.
     */
    @Query("""
            select d from ResearchDocument d
              join fetch d.stock
            where d.source = :source
              and (d.promptVersion is null or d.promptVersion <> :promptVersion)
              and d.publishedAt >= :since
            order by d.publishedAt desc
            """)
    List<ResearchDocument> findPendingSummary(
            @Param("source") ResearchDocument.Source source,
            @Param("promptVersion") String promptVersion,
            @Param("since") Instant since,
            Limit limit);

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
