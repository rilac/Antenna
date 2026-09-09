package ssafy.a507.backend.domain.research.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 리서치 화면의 3열. 열마다 배열을 따로 내려준다 — 화면이 kind 로 다시 나누지 않아도 되고,
 * 한 열이 비어도 나머지 두 열은 그대로 그려진다.
 *
 * <p>{@code targetDate} 는 어느 거래일 기준인지다. 시세·공시·뉴스가 들어오는 시각이 제각각이라
 * 화면이 "9/8 종가 기준" 처럼 적어 줘야 한다. 포인트가 하나도 없으면 null.
 */
public record ResearchPointListResponse(
        List<ResearchPointItemResponse> positive,
        List<ResearchPointItemResponse> risk,
        List<ResearchPointItemResponse> check,
        LocalDate targetDate) {}
