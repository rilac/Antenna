package ssafy.a507.backend.domain.prediction.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.common.security.SignatureGuard;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.market.entity.DailyQuote;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.prediction.commit.CommitHashes;
import ssafy.a507.backend.domain.prediction.commit.PredictionCommitFactory;
import ssafy.a507.backend.domain.prediction.dto.PredictionCreateRequest;
import ssafy.a507.backend.domain.prediction.dto.PredictionCreateResponse;
import ssafy.a507.backend.domain.prediction.dto.PredictionSlotResponse;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.entity.PredictionCommit;
import ssafy.a507.backend.domain.prediction.entity.PredictionEvidence;
import ssafy.a507.backend.domain.prediction.entity.PredictionNote;
import ssafy.a507.backend.domain.prediction.repository.PredictionCommitRepository;
import ssafy.a507.backend.domain.prediction.repository.PredictionEvidenceRepository;
import ssafy.a507.backend.domain.prediction.repository.PredictionNoteRepository;
import ssafy.a507.backend.domain.prediction.repository.PredictionRepository;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;
import ssafy.a507.backend.domain.research.repository.ResearchPointRepository;

/**
 * 예측 등록 (ANT-PRED-01). 검사 → 서명 확인 → 봉인 → 4개 테이블 저장을 한 트랜잭션으로.
 *
 * <p><b>검사 순서가 곧 설계다.</b> 서명 검증은 nonce 를 1회용으로 태우므로 맨 뒤에 둔다 — 어차피 실패할 요청(없는 종목,
 * 방향 모순, 슬롯 초과)이 nonce 를 태우면 사용자는 지갑 팝업을 한 번 더 겪는다. {@code SignatureGuard} 가 "지갑 미연동은
 * nonce 태우기 전에" 라고 한 것과 같은 원칙이다.
 *
 * <p>슬롯(하루 3건)은 <b>사용자 행 잠금</b>으로 지킨다. 세고-저장 사이에 같은 사용자의 요청이 또 오면 4건이 되는데,
 * {@code users} 행을 {@code PESSIMISTIC_WRITE} 로 잡으면 그 사용자의 등록만 줄을 선다. 남에게는 영향이 없다.
 *
 * <p>슬롯 초과는 지금 409 로 끝난다(plan §5-①). 소각할 토큰(ANT-CHAIN-03)이 생기면 여기서 202 소각 경로로 갈라진다.
 */
@Service
public class PredictionRegisterService {

    /** 슬롯·D-day 는 장 기준이라 서버 시간대와 무관하게 KST 로 센다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** REAL 트랙 기간(캘린더일). DB CHECK(ck_predictions_horizon)와 같은 집합 — 앱이 먼저 400 으로 거른다. */
    private static final Set<Short> HORIZONS = Set.of((short) 7, (short) 14, (short) 30, (short) 90);

    /** 근거 포인트 상한. 화면(B-03)이 고를 수 있는 개수 정도. */
    private static final int MAX_EVIDENCES = 10;

    /** 직전 종가를 찾는 창. 상장 종목은 D+1 수집이 돌아 보통 1~4일 안에 있다. */
    private static final int QUOTE_LOOKBACK_DAYS = 30;

    private final EntityManager em;
    private final PredictionRepository predictions;
    private final PredictionNoteRepository notes;
    private final PredictionEvidenceRepository evidences;
    private final PredictionCommitRepository commits;
    private final StockRepository stocks;
    private final DailyQuoteRepository quotes;
    private final ResearchPointRepository researchPoints;
    private final SignatureGuard signatureGuard;
    private final PredictionCommitFactory commitFactory;
    private final int freePerDay;
    private final String overCostWei;

    public PredictionRegisterService(
            EntityManager em,
            PredictionRepository predictions,
            PredictionNoteRepository notes,
            PredictionEvidenceRepository evidences,
            PredictionCommitRepository commits,
            StockRepository stocks,
            DailyQuoteRepository quotes,
            ResearchPointRepository researchPoints,
            SignatureGuard signatureGuard,
            PredictionCommitFactory commitFactory,
            @Value("${app.prediction.slot.free-per-day:3}") int freePerDay,
            // 잠정 2,000 ANT(10^18 단위). 금액표는 ANT-TOKEN-08 이 확정한다 — 값만 바꾸면 된다.
            @Value("${app.prediction.slot.over-cost-wei:2000000000000000000000}") String overCostWei) {
        this.em = em;
        this.predictions = predictions;
        this.notes = notes;
        this.evidences = evidences;
        this.commits = commits;
        this.stocks = stocks;
        this.quotes = quotes;
        this.researchPoints = researchPoints;
        this.signatureGuard = signatureGuard;
        this.commitFactory = commitFactory;
        this.freePerDay = freePerDay;
        this.overCostWei = overCostWei;
    }

    /** 등록. 성공하면 예측 id — 멱등 저장소에 이 값만 남기고 응답은 {@link #created} 가 다시 만든다. */
    @Transactional
    public long register(Long userId, PredictionCreateRequest req) {
        // ① 사용자 행 잠금 — 이 사용자의 등록을 직렬화한다. 지갑 미연동은 SignatureGuard 가 nonce 전에 거른다.
        User user = em.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        // ② 싼 검사들 — 전부 nonce 소비 앞.
        if (!HORIZONS.contains(req.horizon())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "horizon");
        }
        Stock stock = stocks.findById(req.stockCode())
                .filter(Stock::isListed)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND, "stockCode"));
        LocalDate today = LocalDate.now(KST);
        BigDecimal refClose = latestClose(stock, today);
        requireDirectionMatches(req.direction(), req.targetPrice(), refClose);
        List<ResearchPoint> points = loadEvidences(req.evidencePointIdsOrEmpty(), stock);
        CommitHashes.requireNoteSalt(req.noteSalt());

        // ③ 슬롯 — 잠금 안에서 센다.
        if (usedToday(userId, today) >= freePerDay) {
            throw new BusinessException(ErrorCode.PREDICTION_SLOT_EXCEEDED);
        }

        // ④ 서명 — 여기서 nonce 가 소비된다. 이 뒤로 실패하면 사용자는 다시 서명해야 한다.
        String signer = signatureGuard.verify(userId, req);

        // ⑤ 저장. 예측 → 근거 → 리서치 포인트 → 커밋. 전부 같은 트랜잭션 — 커밋 없는 예측은 영영 앵커되지 않는다.
        LocalDate baseDate = BusinessDays.nextWeekday(today);
        Prediction prediction = predictions.save(Prediction.register(
                user,
                stock,
                req.direction(),
                req.targetPrice(),
                refClose,
                req.horizon(),
                baseDate,
                baseDate.plusDays(req.horizon())));
        notes.save(PredictionNote.of(prediction, req.note()));
        evidences.saveAll(points.stream().map(p -> PredictionEvidence.of(prediction, p)).toList());
        commits.save(commitFactory.seal(prediction, req.note(), req.noteSalt(), req.signature(), signer));
        return prediction.getId();
    }

    /** 201 응답 재료. 멱등 재시도도 이 경로로 같은 응답을 만든다. 남의 예측 id 가 오면 404 로 감춘다. */
    @Transactional(readOnly = true)
    public PredictionCreateResponse created(Long userId, long predictionId) {
        Prediction prediction = predictions.findById(predictionId)
                .filter(p -> p.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PREDICTION_NOT_FOUND, "predictionId"));
        PredictionCommit commit = commits.findById(predictionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        return PredictionCreateResponse.of(prediction, commit);
    }

    @Transactional(readOnly = true)
    public PredictionSlotResponse slots(Long userId) {
        LocalDate today = LocalDate.now(KST);
        int used = (int) usedToday(userId, today);
        return new PredictionSlotResponse(today, freePerDay, used, Math.max(0, freePerDay - used), overCostWei);
    }

    private long usedToday(Long userId, LocalDate today) {
        Instant from = today.atStartOfDay(KST).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(KST).toInstant();
        return predictions.countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, from, to);
    }

    /** 직전 종가(ref_close). 최근 30일 안에 시세가 한 건도 없으면 방향 판정을 할 수 없으니 400. */
    private BigDecimal latestClose(Stock stock, LocalDate today) {
        List<DailyQuote> recent = quotes.findByStock_CodeAndTradeDateBetweenOrderByTradeDate(
                stock.getCode(), today.minusDays(QUOTE_LOOKBACK_DAYS), today);
        if (recent.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "stockCode");
        }
        return recent.get(recent.size() - 1).getClose();
    }

    /** UP 인데 직전 종가 이하, DOWN 인데 직전 종가 이상이면 모순. 같으면 둘 다 모순이다. */
    private static void requireDirectionMatches(
            Prediction.Direction direction, BigDecimal targetPrice, BigDecimal refClose) {
        int cmp = targetPrice.compareTo(refClose);
        boolean ok = direction == Prediction.Direction.UP ? cmp > 0 : cmp < 0;
        if (!ok) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "targetPrice");
        }
    }

    /** 근거 포인트: 중복 제거, 상한, 전부 존재, 전부 같은 종목. 순서는 보존하지 않는다(집합). */
    private List<ResearchPoint> loadEvidences(List<Long> ids, Stock stock) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        if (unique.isEmpty()) {
            return List.of();
        }
        if (unique.size() > MAX_EVIDENCES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "evidencePointIds");
        }
        List<ResearchPoint> points = researchPoints.findAllById(unique);
        boolean allSameStock = points.stream().allMatch(p -> p.getStock().getCode().equals(stock.getCode()));
        if (points.size() != unique.size() || !allSameStock) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "evidencePointIds");
        }
        return points;
    }
}
