package ssafy.a507.backend.domain.ranking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.common.Track;
import ssafy.a507.backend.domain.ranking.repository.RankingAggregateRepository;
import ssafy.a507.backend.domain.ranking.repository.RankingAggregateRepository.Aggregate;
import ssafy.a507.backend.domain.ranking.repository.RankingAggregateRepository.Row;

/**
 * 랭킹 스냅샷 배치 B3 (ANT-RANK-01).
 *
 * <p>판정이 끝난 예측만 모아 트랙×필터 조합마다 순위를 매겨 {@code rankings} 를 전량 재작성한다.
 * 조회(ANT-RANK-02·03)는 이 표를 읽기만 하므로, 요청 시점 재계산은 어디에도 없다.
 *
 * <p><b>순위는 {@code ROW_NUMBER} 처럼 매긴다 — 동점에도 번호를 나눠 준다.</b> 조회 응답에
 * {@code nextCursor} 가 없어 프론트가 "받은 마지막 rank + 1" 로 다음 장을 부르기 때문이다.
 * 동점에 같은 rank 를 주면(=SQL 의 {@code RANK()}) 그 등수의 뒷줄이 페이지 경계에서 통째로
 * 건너뛰어진다. 조회 쪽 리포지토리 주석이 이 계약을 명시하고 있고, 여기가 그것을 지키는 자리다.
 *
 * <p><b>REAL 과 REPLAY 는 절대 섞이지 않는다</b>(AC). 집계 쿼리가 트랙을 조건으로 걸고,
 * 필터 조합도 트랙별로 따로 돈다.
 *
 * <p>ponytail: Redis 캐시를 두지 않았다. AC 는 "Redis 스냅샷" 이라고 하지만, 재계산을 막는 일은
 * 이미 {@code rankings} 표가 하고 있고 조회는 인덱스 한 번 타는 단건 쿼리다. 캐시를 붙이면
 * 닉네임 조인 결과까지 직렬화해 두어야 하고 무효화 지점이 하나 늘어난다. 조회가 느리다는 측정이
 * 나오면 {@code RankingQueryService.list} 안쪽 한 곳에 넣으면 되고, 응답 계약은 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingSnapshotService {

    /** 최근 30일 필터의 기준 일수. 명세의 period 어휘 {@code D30} 이 이것이다. */
    private static final int RECENT_DAYS = 30;

    /** 판정·영업일 기준이라 서버 시간대와 무관하게 KST 로 센다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RankingAggregateRepository aggregates;

    /**
     * 한 회차. 실패해도 다음 회차가 전량 재계산하므로 되돌릴 것이 없다 — 재실행 안전이 AC 다.
     *
     * <p>판정 배치(B2 · ANT-PRED-04)가 생기면 그 끝에서 이 메서드를 부르면 된다. 지금은 그 배치가
     * 없어 스케줄러가 따로 돈다.
     */
    @Transactional
    public void runOnce() {
        Instant computedAt = Instant.now();
        LocalDate since = LocalDate.now(KST).minusDays(RECENT_DAYS);

        int filters = 0;
        for (Track track : Track.values()) {
            filters += snapshot(track, null, null, null, null, computedAt);
            if (track == Track.REAL) {
                filters += snapshot(track, "D30", null, null, since, computedAt);
                for (String sector : aggregates.sectorsWithJudgedPredictions(track)) {
                    filters += snapshot(track, null, sector, null, null, computedAt);
                    filters += snapshot(track, "D30", sector, null, since, computedAt);
                }
            } else {
                // 기간·섹터 필터는 실전만이다 — 리플레이는 시즌이 그 자리를 대신한다(명세 §랭킹).
                for (Long seasonId : aggregates.seasonsWithJudgedPredictions(track)) {
                    filters += snapshot(track, null, null, seasonId, null, computedAt);
                }
            }
        }
        log.info("랭킹 스냅샷 완료 — 필터 {}개, 산출 시각 {}", filters, computedAt);
    }

    /**
     * 필터 하나를 산출한다.
     *
     * <p><b>섹터 어휘는 {@code stocks.sector} 의 실제 값(KRX 업종명)이다.</b> 프론트 랭킹 화면이
     * 6개 테마({@code 반도체·2차전지·…})를 상수로 들고 있는데 그것과는 1:1 이 아니다 —
     * {@code 전기·전자} 안에 반도체가, {@code 화학} 안에 2차전지가 섞여 있어 어느 쪽으로 옮겨도 뜻이
     * 바뀐다. 종목 탐색은 이미 서버가 주는 업종명을 그대로 칩으로 쓰므로({@code GET /stocks/sectors}),
     * 앱 안에서 섹터 목록이 두 벌이 되지 않도록 DB 값을 기준으로 삼았다. 랭킹 화면의 상수 교체가
     * 남은 후속이다.
     *
     * <p><b>리플레이는 시즌이 필터가 된다</b>({@code SEASON:{시즌id}}). 조회(ANT-RANK-02·03)가
     * {@code seasonId} 를 받으면 그 키를 찾으므로 배치가 같은 키를 써 두어야 한다 — 안 만들면
     * 오류가 아니라 빈 목록·204 가 조용히 나간다. 시즌을 가르지 않은 {@code ALL} 도 함께 남긴다.
     * {@code seasonId} 없이 오는 리플레이 조회가 그 키를 쓰기 때문이다.
     *
     * @return 이 호출이 쓴 필터 수(항상 1) — 로그용
     */
    private int snapshot(
            Track track,
            String period,
            String sector,
            Long seasonId,
            LocalDate since,
            Instant computedAt) {
        /* 필터 키는 REAL 의 seasonId 를 무시한다(명세 §랭킹 — 트랙에 맞지 않는 파라미터는 무시).
           집계도 같이 무시해야 한다. 한쪽만 시즌을 거르면 키는 ALL 인데 내용은 시즌별인 행이 생기고,
           그것이 바로 이 스토리가 고친 종류의 어긋남이다 — 오류가 아니라 조용히 틀린 목록이 된다. */
        Long season = track == Track.REPLAY ? seasonId : null;

        String filterKey = RankingFilterKey.of(track, period, sector, season);
        List<Aggregate> found = aggregates.aggregate(track, sector, since, season);

        List<Row> rows = rank(found);
        aggregates.replaceFilter(track, filterKey, rows, computedAt);
        log.debug("{} {} — {}명", track, filterKey, rows.size());
        return 1;
    }

    /**
     * 점수 내림차순으로 1부터 번호를 붙인다. 동점이면 user id 오름차순 — 조회의 정렬
     * ({@code rank, user_id})과 같은 기준이어야 페이지 경계가 어긋나지 않는다.
     *
     * <p><b>표본이 모자라면 아예 빼고 번호를 매긴다</b>({@link RankingScore#qualifies}).
     * 점수 가중치로 낮추는 것만으로는 목록에서 사라지지 않아, 1 건 맞힌 사람이 "신뢰도 랭킹" 에
     * 이름을 올린다. 명세가 {@code GET /rankings/me} 의 204 를 "표본 부족" 으로 설명하는 것도
     * 이 규칙을 전제한 것이다. 거른 뒤에 번호를 붙이므로 순위는 여전히 1 부터 촘촘하다.
     */
    private List<Row> rank(List<Aggregate> found) {
        List<Aggregate> sorted = found.stream()
                .filter(a -> RankingScore.qualifies(a.doneCount()))
                .sorted(Comparator.comparing(
                                (Aggregate a) -> RankingScore.of(a.doneCount(), a.hitCount(), a.avgError()))
                        .reversed()
                        .thenComparingLong(Aggregate::userId))
                .toList();

        List<Row> rows = new java.util.ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            Aggregate a = sorted.get(i);
            BigDecimal score = RankingScore.of(a.doneCount(), a.hitCount(), a.avgError());
            rows.add(new Row(
                    a.userId(),
                    i + 1,
                    score,
                    RankingScore.hitRatePercent(a.doneCount(), a.hitCount()),
                    a.avgError(),
                    a.doneCount()));
        }
        return rows;
    }
}
