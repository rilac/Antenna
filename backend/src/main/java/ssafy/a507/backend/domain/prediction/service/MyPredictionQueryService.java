package ssafy.a507.backend.domain.prediction.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.prediction.dto.MyPredictionItemResponse;
import ssafy.a507.backend.domain.prediction.dto.MyPredictionListResponse;
import ssafy.a507.backend.domain.prediction.entity.Prediction;
import ssafy.a507.backend.domain.prediction.repository.PredictionRepository;

/**
 * 내 예측 목록 (ANT-PRED-06, 화면 C-02).
 *
 * <p>읽기만 한다. 판정·기준가는 배치(ANT-PRED-03)가 채운 값을 그대로 보여줄 뿐이고, 여기서 계산하는 것은
 * D-day 와 적중률 두 개뿐이다.
 *
 * <p>게이팅이 없다 — 자기 예측만 돌려주므로 미판정(BASE/OPEN) 건도 그대로 보인다. 남의 예측을 감추는 쪽은
 * 상세(GET /predictions/{id})와 proof 의 몫이다.
 */
@Service
@RequiredArgsConstructor
public class MyPredictionQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** 만기·D-day 는 장 기준이라 서버 시간대와 무관하게 KST 로 센다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PredictionRepository predictions;

    /**
     * status 파라미터 어휘(명세 §예측). 저장된 상태 네 값과 1:1 이 아니라 {@code PENDING} 이
     * BASE·OPEN 을 묶는다 — 화면은 "대기 / 적중 / 실패" 세 칸으로 보여주고 기준가 확정 여부는 구분하지 않는다.
     * 어휘 밖 값은 스프링의 변환 실패로 400 이다.
     */
    public enum StatusFilter {
        ALL(EnumSet.allOf(Prediction.Status.class)),
        PENDING(EnumSet.of(Prediction.Status.BASE, Prediction.Status.OPEN)),
        HIT(EnumSet.of(Prediction.Status.HIT)),
        MISS(EnumSet.of(Prediction.Status.MISS));

        private final Set<Prediction.Status> statuses;

        StatusFilter(Set<Prediction.Status> statuses) {
            this.statuses = statuses;
        }
    }

    @Transactional(readOnly = true)
    public MyPredictionListResponse list(Long userId, StatusFilter status, Long cursor, Integer size) {
        int pageSize = pageSize(size);
        StatusFilter filter = status == null ? StatusFilter.ALL : status;

        List<Prediction> found =
                predictions.findMyPage(userId, filter.statuses, cursor, Limit.of(pageSize + 1));
        boolean hasNext = found.size() > pageSize;
        List<Prediction> page = hasNext ? found.subList(0, pageSize) : found;

        LocalDate today = LocalDate.now(KST);
        List<MyPredictionItemResponse> items = page.stream()
                .map(p -> MyPredictionItemResponse.of(p, today))
                .toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return summary(userId).toResponse(items, nextCursor, hasNext);
    }

    /** 상태 분포 한 번 조회로 네 값을 다 만든다. 필터가 걸려 있어도 집계는 전량 기준이다. */
    private Summary summary(Long userId) {
        long pending = 0;
        long hit = 0;
        long miss = 0;
        for (Object[] row : predictions.countMineByStatus(userId)) {
            long count = ((Number) row[1]).longValue();
            switch ((Prediction.Status) row[0]) {
                case HIT -> hit = count;
                case MISS -> miss = count;
                case BASE, OPEN -> pending += count;
            }
        }
        return new Summary(pending, hit, miss);
    }

    private record Summary(long pending, long hit, long miss) {

        MyPredictionListResponse toResponse(
                List<MyPredictionItemResponse> items, Long nextCursor, boolean hasNext) {
            long judged = hit + miss;
            BigDecimal hitRate = judged == 0
                    ? null
                    : BigDecimal.valueOf(hit)
                            .divide(BigDecimal.valueOf(judged), 4, RoundingMode.HALF_UP);
            return new MyPredictionListResponse(
                    items, nextCursor, hasNext, pending + judged, pending, judged, hitRate);
        }
    }

    private int pageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
