package ssafy.a507.backend.domain.research.service;

import java.util.ArrayList;
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
 * <p>여기서 LLM 을 부르지 않는다. 관련도 판정은 밤 배치가 미리 남긴 것을 읽기만 한다 —
 * 사용자 요청이 외부 생성 API 의 지연과 장애에 묶이면 안 된다(API 명세 §5.5).
 *
 * <p>무관 기사는 목록에서 뺀다({@link NewsRelevanceFilter}). 투자 포인트 재료와 같은 규칙이다 —
 * 포인트에 안 쓰인 기사가 목록에만 뜨면 사용자가 근거를 찾다가 어긋난다.
 */
@Service
@RequiredArgsConstructor
public class ResearchDocumentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    /** 한 요청이 훑을 수 있는 최대 횟수. 무관 기사만 있는 종목에서 표 전체를 훑지 않게 한다. */
    private static final int MAX_ROUNDS = 5;

    /** 한 번에 요청한 장의 몇 배를 읽을지. 걸러 낼 것을 감안한 여유다. */
    private static final int SCAN_FACTOR = 4;

    /** 한 번에 읽는 최대 건수. 장 크기가 커도 한 질의가 지나치게 무거워지지 않게 한다. */
    private static final int MAX_SCAN_CHUNK = 200;

    private final StockRepository stockRepository;
    private final ResearchDocumentRepository researchDocumentRepository;
    private final NewsRelevanceFilter newsRelevanceFilter;

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
        Scan scan = scan(stockCode, source, cursor, pageSize);

        boolean truncated = scan.kept().size() > pageSize;
        List<ResearchDocument> documents = truncated ? scan.kept().subList(0, pageSize) : scan.kept();
        if (documents.isEmpty()) {
            // 읽을 것이 남았는데 이 구간이 통째로 무관이었을 수 있다. 그때는 커서를 들려 보내
            // 다음 요청이 그 뒤부터 읽게 한다 — 아직 안 본 기사를 잃지 않는다.
            return new ResearchDocumentListResponse(
                    List.of(), scan.exhausted() ? null : scan.lastScannedId(), !scan.exhausted());
        }

        List<ResearchDocumentItemResponse> items =
                documents.stream().map(ResearchDocumentItemResponse::of).toList();
        boolean hasNext = truncated || !scan.exhausted();
        Long nextCursor = hasNext
                ? (truncated ? documents.get(documents.size() - 1).getId() : scan.lastScannedId())
                : null;
        return new ResearchDocumentListResponse(items, nextCursor, hasNext);
    }

    /**
     * 한 장을 채울 때까지 읽는다.
     *
     * <p>무관 기사를 뒤에서 걸러 내므로 {@code pageSize + 1} 만 읽으면 장이 빈 채로 내려간다 —
     * 구단 이름이 곧 회사명인 종목(두산·한화·KT…)은 한 구간이 통째로 야구 기사인 날이 있다.
     * 그래서 한 번에 넉넉히 읽고, 모자라면 커서를 밀어 몇 번 더 읽는다.
     *
     * <p>{@code MAX_ROUNDS} 로 끊는 이유는 무관 기사만 수천 건인 종목에서 한 요청이 표 전체를
     * 훑지 않게 하려는 것이다. 다 못 채우면 커서를 들려 보내고 다음 요청이 이어 읽는다.
     */
    private Scan scan(String stockCode, ResearchDocument.Source source, Long cursor, int pageSize) {
        List<ResearchDocument> kept = new ArrayList<>();
        Long lastScannedId = cursor;
        boolean exhausted = false;
        int chunk = Math.min((pageSize + 1) * SCAN_FACTOR, MAX_SCAN_CHUNK);
        for (int round = 0; round < MAX_ROUNDS && kept.size() <= pageSize; round++) {
            List<ResearchDocument> found = researchDocumentRepository.findPageByStock(
                    stockCode, source, lastScannedId, Limit.of(chunk));
            if (found.isEmpty()) {
                exhausted = true;
                break;
            }
            lastScannedId = found.get(found.size() - 1).getId();
            kept.addAll(newsRelevanceFilter.keepRelevant(found));
            if (found.size() < chunk) {
                exhausted = true;
                break;
            }
        }
        return new Scan(kept, lastScannedId, exhausted);
    }

    /**
     * @param kept 걸러 남은 문서 · {@code pageSize} 를 넘을 수 있다
     * @param lastScannedId 이번에 훑은 마지막 문서 id · 남은 것이 있을 때 다음 커서가 된다
     * @param exhausted 이 종목·원천의 문서를 끝까지 읽었나
     */
    private record Scan(List<ResearchDocument> kept, Long lastScannedId, boolean exhausted) {}

    private int pageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
