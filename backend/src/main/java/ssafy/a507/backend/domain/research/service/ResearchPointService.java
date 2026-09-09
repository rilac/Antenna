package ssafy.a507.backend.domain.research.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.dto.ResearchPointItemResponse;
import ssafy.a507.backend.domain.research.dto.ResearchPointListResponse;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;
import ssafy.a507.backend.domain.research.repository.ResearchPointRepository;

/**
 * 긍정·위험·확인 포인트 조회 (ANT-RESEARCH-04).
 *
 * <p><b>없으면 그 자리에서 만든다(2026-09-09 부터).</b> 예전엔 배치가 미리 만든 것을 읽기만 했다.
 * 지금은 {@code date} 없이 부르면 최신 거래일 포인트가 없을 때 {@link ResearchPointGenerationService}
 * 를 불러 2~4초 안에 만들고 돌려준다. 같은 종목·같은 거래일은 한 번만 만든다.
 *
 * <p>트랜잭션을 걸지 않는다 — 생성이 LLM 을 기다리는 동안 DB 연결을 잡고 있을 이유가 없고,
 * readOnly 트랜잭션 안에서는 생성 결과가 저장되지 않는다. 읽기 쿼리 셋은 각자 짧게 돈다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchPointService {

    private final StockRepository stockRepository;
    private final ResearchPointRepository researchPointRepository;
    private final ResearchPointGenerationService generationService;

    /**
     * 한 종목의 3열. {@code date} 가 없으면 최신 거래일 기준 — 없으면 만들고, 그래도 없으면(키 없음·
     * 시세 없음·전부 버려짐) 포인트가 있는 가장 최근 영업일이다. {@code date} 를 주면 읽기만 한다.
     *
     * <p>아직 포인트가 없는 종목은 200 + 빈 3열이다. 404 로 만들면 화면이 "종목이 없다"와
     * "오늘 포인트가 아직 없다"를 구분하지 못한다. 생성이 실패한 경우만 503 — 화면이 다시 시도를 연다.
     */
    public ResearchPointListResponse points(String stockCode, LocalDate date) {
        Stock stock = stockRepository.findById(stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND, "code"));
        if (date == null) {
            generateOrThrow(stock);
        }
        LocalDate targetDate = date != null
                ? date
                : researchPointRepository.findLatestTargetDate(stockCode).orElse(null);
        if (targetDate == null) {
            return new ResearchPointListResponse(List.of(), List.of(), List.of(), null);
        }
        Map<ResearchPoint.Kind, List<ResearchPointItemResponse>> byKind =
                researchPointRepository.findOn(stockCode, targetDate).stream()
                        .collect(Collectors.groupingBy(
                                ResearchPoint::getKind,
                                Collectors.mapping(ResearchPointItemResponse::of, Collectors.toList())));
        return new ResearchPointListResponse(
                byKind.getOrDefault(ResearchPoint.Kind.POSITIVE, List.of()),
                byKind.getOrDefault(ResearchPoint.Kind.RISK, List.of()),
                byKind.getOrDefault(ResearchPoint.Kind.CHECK, List.of()),
                targetDate);
    }

    private void generateOrThrow(Stock stock) {
        try {
            generationService.generate(stock);
        } catch (AiException e) {
            log.warn("[POINT] 생성 실패 stock={} — {}", stock.getCode(), e.getMessage());
            throw new BusinessException(ErrorCode.POINT_GENERATION_FAILED);
        }
    }
}
