package ssafy.a507.backend.domain.chain.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.chain.dto.AnchorDetailResponse;
import ssafy.a507.backend.domain.chain.dto.AnchorItemResponse;
import ssafy.a507.backend.domain.chain.dto.AnchorListResponse;
import ssafy.a507.backend.domain.chain.entity.AnchorBatch;
import ssafy.a507.backend.domain.chain.repository.AnchorBatchRepository;
import ssafy.a507.backend.domain.prediction.entity.PredictionCommit;
import ssafy.a507.backend.domain.prediction.repository.PredictionCommitRepository;

/**
 * 앵커 배치 조회 (ANT-CHAIN-06, 화면 D-01·D-02).
 *
 * <p>읽기만 한다. 체인에는 묻지 않는다 — "체인에 정말 박혔나" 는 브라우저가 {@code rootOf(batchId)} 로 직접 대조한다
 * (화면설계서 D-03: 서버가 "검증됨" 이라 말해 주면 그건 증명이 아니다). 여기가 내리는 값은 전부 DB 의 것이고,
 * 릴레이어(CHAIN-05)와 인덱서(CHAIN-04)가 채운 그대로다.
 *
 * <p>게이팅이 없다. 배치 목록·루트·커밋 해시는 회원이면 누구나 본다(결정 F2·F3). 해시만으로는 예측 내용을 알 수 없다.
 */
@Service
@RequiredArgsConstructor
public class AnchorQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AnchorBatchRepository batches;
    private final PredictionCommitRepository commits;

    /** 최신 배치부터. 커서는 마지막으로 받은 id 다 — 그보다 작은 id 를 다음 페이지로 준다. */
    @Transactional(readOnly = true)
    public AnchorListResponse list(Long cursor, Integer size) {
        int pageSize = pageSize(size);
        List<AnchorBatch> found = batches.findPage(cursor, Limit.of(pageSize + 1));
        boolean hasNext = found.size() > pageSize;
        List<AnchorBatch> page = hasNext ? found.subList(0, pageSize) : found;
        if (page.isEmpty()) {
            return new AnchorListResponse(List.of(), null, false);
        }
        List<AnchorItemResponse> items = page.stream().map(AnchorItemResponse::of).toList();
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new AnchorListResponse(items, nextCursor, hasNext);
    }

    /**
     * 배치 하나 + 리프 전량. 커밋 해시 순서는 {@code prediction id 오름차순} — 배치(CHAIN-02)가 트리를 만든 순서이고
     * 컨트랙트 이벤트에 남은 순서다. 이 순서를 바꾸면 같은 해시 집합이라도 루트가 달라진다.
     */
    @Transactional(readOnly = true)
    public AnchorDetailResponse detail(long id) {
        AnchorBatch batch = batches.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANCHOR_NOT_FOUND, "id"));
        List<String> hashes = commits.findByAnchorBatchOrderByPredictionIdAsc(batch).stream()
                .map(PredictionCommit::getCommitHash)
                .toList();
        return AnchorDetailResponse.of(batch, hashes);
    }

    private int pageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
