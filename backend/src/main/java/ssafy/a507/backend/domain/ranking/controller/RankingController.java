package ssafy.a507.backend.domain.ranking.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.common.Track;
import ssafy.a507.backend.domain.ranking.dto.RankingListResponse;
import ssafy.a507.backend.domain.ranking.service.RankingQueryService;

/**
 * 예측가 랭킹 — ANT-RANK-02, 화면 E-01(랭킹) · B-01(인기 예측가 위젯).
 *
 * <p>위젯은 별도 API 없이 {@code ?track=REAL&limit=5} 로 이 엔드포인트를 재사용한다(설계 결정 Q17).
 *
 * <p>{@code track} 은 필수다. 어휘 밖 값이면 스프링의 enum 변환이 실패해 400 이 된다 —
 * 실전과 리플레이를 섞어 보여주는 기본값을 두느니 400 이 낫다. 리플레이 실적은 실전 신뢰도에 절대 섞이지 않는다.
 */
@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingQueryService rankingQueryService;

    @GetMapping
    public RankingListResponse list(
            @RequestParam Track track,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer cursor,
            @RequestParam(required = false) Integer fromRank) {
        return rankingQueryService.list(track, period, sector, seasonId, limit, cursor, fromRank);
    }
}
