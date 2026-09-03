package ssafy.a507.backend.domain.research.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.dto.ResearchDocumentItemResponse;
import ssafy.a507.backend.domain.research.dto.ResearchDocumentListResponse;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * 종목별 뉴스·공시 조회 (ANT-RESEARCH-02).
 *
 * <p>여기서 LLM 을 부르지 않는다. 요약은 배치 B6 가 미리 만들어 둔 것을 읽기만 한다 —
 * 사용자 요청이 외부 생성 API 의 지연과 장애에 묶이면 안 된다(API 명세 §5.5).
 */
@Service
@RequiredArgsConstructor
public class ResearchDocumentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final StockRepository stockRepository;
    private final ResearchDocumentRepository researchDocumentRepository;

    /**
     * 종목의 문서 한 페이지. 최신순 고정이다.
     *
     * @param source null 이면 원천을 가리지 않는다
     * @param cursor 직전 페이지 마지막 항목의 id · null 이면 첫 장
     */
    @Transactional(readOnly = true)
    public ResearchDocumentListResponse documents(
            String stockCode, ResearchDocument.Source source, Long cursor, Integer size) {
        if (!stockRepository.existsById(stockCode)) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "code");
        }

        int pageSize = pageSize(size);
        // 한 건 더 읽어 다음 페이지가 있는지 본다. count 쿼리를 따로 돌리지 않는다.
        List<ResearchDocument> found = researchDocumentRepository.findPageByStock(
                stockCode, source, cursor, Limit.of(pageSize + 1));

        boolean hasNext = found.size() > pageSize;
        List<ResearchDocument> documents = hasNext ? found.subList(0, pageSize) : found;
        if (documents.isEmpty()) {
            return new ResearchDocumentListResponse(List.of(), null, false);
        }

        List<ResearchDocumentItemResponse> items =
                documents.stream().map(ResearchDocumentItemResponse::of).toList();
        Long nextCursor = hasNext ? documents.get(documents.size() - 1).getId() : null;
        return new ResearchDocumentListResponse(items, nextCursor, hasNext);
    }

    private int pageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
