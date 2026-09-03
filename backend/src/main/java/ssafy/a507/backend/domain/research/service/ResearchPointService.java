package ssafy.a507.backend.domain.research.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.dto.ResearchPointItemResponse;
import ssafy.a507.backend.domain.research.dto.ResearchPointListResponse;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;
import ssafy.a507.backend.domain.research.repository.ResearchPointRepository;

/**
 * 긍정·위험·확인 포인트 조회 (ANT-RESEARCH-04). 배치 B6 가 만들어 둔 것을 읽기만 한다 — 여기서
 * LLM 을 부르지 않는다(브리핑·뉴스 요약과 같은 규칙).
 */
@Service
@RequiredArgsConstructor
public class ResearchPointService {

    private final StockRepository stockRepository;
    private final ResearchPointRepository researchPointRepository;

    /**
     * 한 종목의 3열. {@code date} 가 없으면 포인트가 있는 가장 최근 영업일이다.
     *
     * <p>아직 배치가 돌지 않은 종목은 200 + 빈 3열이다. 404 로 만들면 화면이 "종목이 없다"와
     * "오늘 포인트가 아직 없다"를 구분하지 못한다.
     */
    @Transactional(readOnly = true)
    public ResearchPointListResponse points(String stockCode, LocalDate date) {
        if (!stockRepository.existsById(stockCode)) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "code");
        }
        LocalDate targetDate = date != null
                ? date
                : researchPointRepository.findLatestTargetDate(stockCode).orElse(null);
        if (targetDate == null) {
            return new ResearchPointListResponse(List.of(), List.of(), List.of());
        }
        Map<ResearchPoint.Kind, List<ResearchPointItemResponse>> byKind =
                researchPointRepository.findOn(stockCode, targetDate).stream()
                        .collect(Collectors.groupingBy(
                                ResearchPoint::getKind,
                                Collectors.mapping(ResearchPointItemResponse::of, Collectors.toList())));
        return new ResearchPointListResponse(
                byKind.getOrDefault(ResearchPoint.Kind.POSITIVE, List.of()),
                byKind.getOrDefault(ResearchPoint.Kind.RISK, List.of()),
                byKind.getOrDefault(ResearchPoint.Kind.CHECK, List.of()));
    }
}
