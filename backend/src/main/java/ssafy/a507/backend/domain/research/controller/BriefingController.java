package ssafy.a507.backend.domain.research.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.research.dto.BriefingDetailResponse;
import ssafy.a507.backend.domain.research.dto.BriefingListResponse;
import ssafy.a507.backend.domain.research.entity.AiBriefing;
import ssafy.a507.backend.domain.research.service.BriefingService;

/** 홈 시장 브리핑 카드 · 종목 상세 브리핑 탭 · 브리핑 상세 모달(M-10) — ANT-RESEARCH-03. */
@RestController
@RequestMapping("/api/v1/briefings")
@RequiredArgsConstructor
public class BriefingController {

    private final BriefingService briefingService;

    @GetMapping
    public BriefingListResponse list(
            @RequestParam(required = false) AiBriefing.Scope scope,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return briefingService.list(scope, stockCode, date);
    }

    @GetMapping("/{id}")
    public BriefingDetailResponse detail(@PathVariable Long id) {
        return briefingService.detail(id);
    }
}
