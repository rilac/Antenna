package ssafy.a507.backend.domain.research.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.dto.BriefingDetailResponse;
import ssafy.a507.backend.domain.research.dto.BriefingItemResponse;
import ssafy.a507.backend.domain.research.dto.BriefingListResponse;
import ssafy.a507.backend.domain.research.entity.AiBriefing;
import ssafy.a507.backend.domain.research.repository.AiBriefingRepository;

/**
 * AI 브리핑 조회 (ANT-RESEARCH-03). 배치 B6 가 만들어 둔 것을 읽기만 한다 — 여기서 LLM 을
 * 부르지 않는다(뉴스 요약과 같은 규칙).
 */
@Service
@RequiredArgsConstructor
public class BriefingService {

    private final StockRepository stockRepository;
    private final AiBriefingRepository aiBriefingRepository;

    /**
     * 목록. 필터는 전부 선택이고, {@code date} 가 없으면 필터 안에서 가장 최신 영업일 하루치다.
     * 페이징이 없다 — 한 날짜에 MARKET 1건 + 종목당 1건이라 종목을 지정하면 한두 건이다.
     */
    @Transactional(readOnly = true)
    public BriefingListResponse list(AiBriefing.Scope scope, String stockCode, LocalDate date) {
        if (stockCode != null && !stockRepository.existsById(stockCode)) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "stockCode");
        }
        LocalDate targetDate = date != null
                ? date
                : aiBriefingRepository.findLatestTargetDate(scope, stockCode).orElse(null);
        if (targetDate == null) {
            return new BriefingListResponse(List.of());
        }
        List<BriefingItemResponse> items = aiBriefingRepository.findAllOn(scope, stockCode, targetDate).stream()
                .map(BriefingItemResponse::of)
                .toList();
        return new BriefingListResponse(items);
    }

    @Transactional(readOnly = true)
    public BriefingDetailResponse detail(Long id) {
        return aiBriefingRepository.findById(id)
                .map(BriefingDetailResponse::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.BRIEFING_NOT_FOUND, "id"));
    }
}
