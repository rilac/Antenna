package ssafy.a507.backend.domain.ranking.dto;

import java.math.BigDecimal;
import ssafy.a507.backend.domain.ranking.entity.Ranking;

/**
 * 랭킹 한 줄 (ANT-RANK-02, 화면 E-01 · B-01 인기 예측가 위젯).
 *
 * <p>표시 지표는 신뢰도·적중률·판정 수 세 개다 — 레벨·EXP 는 쓰지 않는다(설계 결정 D11·D12).
 * {@code score·hitRate·avgError} 는 배치가 채우기 전이면 null 이다. 0 으로 내리면 "점수 0점" 으로 읽힌다.
 */
public record RankingItemResponse(
        int rank,
        Long userId,
        String nickname,
        BigDecimal score,
        BigDecimal hitRate,
        BigDecimal avgError,
        int doneCount) {

    public static RankingItemResponse from(Ranking ranking) {
        return new RankingItemResponse(
                ranking.getRank(),
                ranking.getUser().getId(),
                ranking.getUser().getNickname(),
                ranking.getScore(),
                ranking.getHitRate(),
                ranking.getAvgError(),
                ranking.getDoneCount());
    }
}
