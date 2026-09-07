package ssafy.a507.backend.domain.season.dto;

import java.math.BigDecimal;
import ssafy.a507.backend.domain.season.entity.Season;

/**
 * GET /api/v1/seasons 의 한 행. G-01 홈과 G-02a 연습하기가 이 값으로 카드를 그린다.
 *
 * <p><b>시기를 담지 않는다.</b> {@code seasons.base_date} 는 서버가 game_day 를 실제
 * 영업일로 바꿀 때만 쓰는 값이다 — 연도가 새면 참가자가 그다음에 무슨 일이 있었는지 아는
 * 상태로 시작해 예측이 아니라 복기가 된다(API 명세 v0.24 · 설계서 §3 G).
 *
 * @param title 시즌의 <b>성격</b> · 연도·사건 고유명사 금지
 * @param sector 참가자에게 보이는 섹터 힌트 · 상위 분류로만
 * @param tickerCount 블라인드 종목 수 · 이름은 진행 화면에서 "A사" 로만 보인다
 * @param entryFee COMPETITION 만 · 연습·시연은 null
 */
public record SeasonListItemResponse(
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
        Integer entryFee) {}
