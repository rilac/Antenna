package ssafy.a507.backend.domain.prediction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import ssafy.a507.backend.domain.chain.dto.ProofResponse;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.entity.PredictionCommit;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;

/**
 * GET /api/v1/predictions/{id} 200 응답 (ANT-PRED-05, 화면 C-03).
 *
 * <p>잠금이 두 겹이다. ① {@code locked} — 판정 전 비구독자에게 방향·목표가·근거 포인트를 가린다.
 * ② {@code noteLocked} — 근거 본문은 판정 후에도 작성자·구독자만 본다(결정 D6).
 *
 * <p>{@code proof} 는 잠금과 무관하게 항상 온다. 서명 주소·커밋 해시·앵커가 "조작하지 않았다" 를 스스로 증명하는
 * 재료이고, 해시로는 내용을 알 수 없다.
 *
 * <p>명세 스키마에 {@code stockName·horizon·settlePrice·dday·createdAt·locked·noteLocked·lastClose} 와
 * {@code proof.anchorStatus} 를 더했다 — 프론트 {@code PredictionDetail} 타입이 이미 기다리는 값만이다.
 */
public record PredictionDetailResponse(
        long id,
        Author author,
        String stockCode,
        String stockName,
        Prediction.Direction direction,
        BigDecimal targetPrice,
        BigDecimal refClose,
        BigDecimal basePrice,
        LocalDate baseDate,
        LocalDate settleDate,
        BigDecimal settlePrice,
        short horizon,
        Prediction.Status status,
        BigDecimal errorRate,
        Integer dday,
        Instant createdAt,
        boolean locked,
        boolean noteLocked,
        String note,
        List<Evidence> evidences,
        LastClose lastClose,
        Proof proof) {

    public record Author(Long userId, String nickname) {}

    /** 커밋에 묶인 리서치 포인트. 종류(POSITIVE·RISK·CHECK)가 방향을 드러내서 판정 전 비구독자에게는 비어 온다. */
    public record Evidence(Long pointId, ResearchPoint.Kind kind, String body) {

        public static Evidence from(ResearchPoint point) {
            return new Evidence(point.getId(), point.getKind(), point.getBody());
        }
    }

    /** 진행률 재료. 실전 시세는 전일 종가뿐이라 그 날짜를 함께 준다 — "현재가" 로 읽히지 않게(§7 legal). */
    public record LastClose(BigDecimal close, LocalDate tradeDate) {}

    /** 조작 불가 근거. 커밋이 아직 없으면(등록 직후 극히 짧은 창) 해시·주소가 null 이고 anchorStatus 는 WAITING. */
    public record Proof(
            String signerAddress,
            String commitHash,
            ProofResponse.AnchorStatus anchorStatus,
            Long anchorBlockNumber,
            boolean revealed) {}

    /**
     * @param locked 판정 전 + 작성자·구독자가 아님
     * @param noteLocked 작성자·구독자가 아님(판정 여부와 무관)
     * @param note 잠겼거나 근거 행이 없으면 null
     */
    public static PredictionDetailResponse of(
            Prediction p,
            LocalDate today,
            boolean locked,
            boolean noteLocked,
            String note,
            List<Evidence> evidences,
            DailyQuote lastQuote,
            PredictionCommit commit) {
        Stock stock = p.getStock();
        AnchorBatch batch = commit == null ? null : commit.getAnchorBatch();
        return new PredictionDetailResponse(
                p.getId(),
                new Author(p.getUser().getId(), p.getUser().getNickname()),
                stock == null ? null : stock.getCode(),
                stock == null ? null : stock.getName(),
                locked ? null : p.getDirection(),
                locked ? null : p.getTargetPrice(),
                p.getRefClose(),
                p.getBasePrice(),
                p.getBaseDate(),
                p.getSettleDate(),
                p.getSettlePrice(),
                p.getHorizon(),
                p.getStatus(),
                p.getErrorRate(),
                MyPredictionItemResponse.dday(p, today),
                p.getCreatedAt(),
                locked,
                noteLocked,
                noteLocked ? null : note,
                locked ? List.of() : evidences,
                lastQuote == null ? null : new LastClose(lastQuote.getClose(), lastQuote.getTradeDate()),
                new Proof(
                        commit == null ? null : commit.getSignerAddress(),
                        commit == null ? null : commit.getCommitHash(),
                        ProofResponse.AnchorStatus.of(batch),
                        batch == null ? null : batch.getBlockNumber(),
                        commit != null && commit.isRevealed()));
    }
}
