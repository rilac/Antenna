package ssafy.a507.backend.domain.research.dto;

import java.util.List;

/**
 * 리서치 화면의 3열. 열마다 배열을 따로 내려준다 — 화면이 kind 로 다시 나누지 않아도 되고,
 * 한 열이 비어도 나머지 두 열은 그대로 그려진다.
 */
public record ResearchPointListResponse(
        List<ResearchPointItemResponse> positive,
        List<ResearchPointItemResponse> risk,
        List<ResearchPointItemResponse> check) {}
