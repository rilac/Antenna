package ssafy.a507.backend.domain.research.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.research.dto.ResearchPointListResponse;
import ssafy.a507.backend.domain.research.service.ResearchPointService;

/** 종목 리서치 탭의 긍정·위험·확인 3열 — ANT-RESEARCH-04. */
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class ResearchPointController {

    private final ResearchPointService researchPointService;

    /** {@code date} 를 빼면 포인트가 있는 가장 최근 영업일치를 내려준다. */
    @GetMapping("/{code}/points")
    public ResearchPointListResponse points(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return researchPointService.points(code, date);
    }
}
