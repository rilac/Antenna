package ssafy.a507.backend.domain.market.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/stocks 200 응답. 커서 페이징 규약은 명세 §1 을 따른다.
 *
 * @param baseDate 목록의 종가가 기준하는 영업일. 화면이 "종가 기준 YYYY.MM.DD" 를 한 번만
 *     찍을 수 있도록 행이 아니라 응답에 싣는다. 수집된 시세가 하나도 없으면 비어 있다.
 */
public record StockListResponse(
        List<StockListItemResponse> items, LocalDate baseDate, String nextCursor, boolean hasNext) {}
