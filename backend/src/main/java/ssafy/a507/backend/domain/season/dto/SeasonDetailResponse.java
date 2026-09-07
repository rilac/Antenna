package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;
import java.time.Instant;
import ssafy.a507.backend.domain.season.entity.Season;

/**
 * GET /api/v1/seasons/{id} 200 응답 · G-03 시즌 상세·참가.
 *
 * <p>목록과 같은 값에 대회 시간표와 내 참가 여부만 더한다. 목록에 없는 필드는 이 셋이다 —
 * {@code opensAt}·{@code closesAt}·{@code dayIntervalMinutes}(대회만), {@code joined}·
 * {@code currentDay}(내 것).
 *
 * <p><b>여기에도 시기를 담지 않는다.</b> 상세는 참가 직전 화면이라 가장 새기 쉬운 자리다 —
 * 실제 날짜·연도·사건 고유명사는 이 응답에도, /tickers·/news·/prices 에도 나가지 않는다.
 *
 * @param joined 내가 이미 참가한 시즌인가 · 화면이 참가 대신 "이어서 하기" 를 그린다
 * @param currentDay {@code joined} 일 때의 개인 진행 게임일 · 아니면 null
 */
public record SeasonDetailResponse(
        Long id,
        Season.Mode mode,
        Season.Status status,
        String title,
        String note,
        String theme,
        String sector,
        int lengthDays,
        BigDecimal initialCash,
        int tickerCount,
        Integer entryFee,
        Instant opensAt,
        Instant closesAt,
        Integer dayIntervalMinutes,
        boolean joined,
        Integer currentDay) {}
