package ssafy.a507.backend.domain.research.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * GET /api/v1/stocks/{code}/valuation 200 응답.
 *
 * <p>{@code per}·{@code pbr} 는 종가 × 상장주식수를 최신 연간 순이익·자본총계로 나눈 값이다. 탐색
 * 목록의 파생 컬럼과 같은 식·같은 자리수라 두 화면이 같은 숫자를 보여 준다. 재료가 없거나 적자·
 * 자본잠식이면 null 이고 사유는 {@code basedOn.note} 에 적는다 — 키는 남긴다. 화면이
 * {@code v.per === null} 로 분기하므로 키가 빠지면 undefined 가 흘러 렌더가 죽는다.
 *
 * <p>비율은 <b>가장 최근 연간 재무</b> 하나로만 계산한다 — 연도를 섞으면 {@code basedOn.fiscal} 이
 * 어느 숫자의 기준인지 말할 수 없다. 그 해 보고서에 계정이 없으면 그 비율만 null 이고, 나머지는
 * 그대로 값이 있다.
 *
 * @param roe 순이익 / 자본총계 · % 소수 1자리. 최신 연간 재무가 없거나, 순이익·자본총계 계정이
 *     비었거나, 자본총계가 0 이하(완전자본잠식)면 null
 * @param debtRatio 부채총계 / 자본총계 · % 소수 1자리. 부채총계 기준으로 같은 조건에서 null
 */
public record ValuationResponse(
        BigDecimal per, BigDecimal pbr, BigDecimal roe, BigDecimal debtRatio, BasedOn basedOn) {

    /**
     * 수치의 근거. 어느 날 종가·어느 연도 재무제표로 계산했는지 화면이 표기한다.
     *
     * @param priceDate 종가 기준일 — <b>그 종목의 마지막 거래일</b>이다. 전역 최신 거래일이 아니다:
 *     수집 대상이 KOSPI 상위 300 이라 나머지 종목과 거래정지 종목은 그날 종가가 없다. prevClose·
 *     per·pbr 이 모두 이 날짜 한 행에서 나온다 — 화면 머리의 "N일 종가" 가 곧 지표의 기준일이다
     * @param fiscal 재무 기준 회계연도. 재무가 없으면 null
     * @param fsDiv 그 재무의 연결(CFS)·별도(OFS) 구분
     * @param note per·pbr 가 비어 있는 이유 · 둘 다 값이 있으면 null
     */
    public record BasedOn(LocalDate priceDate, BigDecimal prevClose, Integer fiscal, String fsDiv, String note) {}
}
