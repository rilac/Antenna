package ssafy.a507.backend.domain.research.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.research.dto.ResearchDocumentListResponse;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.service.ResearchDocumentService;

/** 종목 리서치 탭의 뉴스·공시 목록 — ANT-RESEARCH-02. */
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class ResearchDocumentController {

    private final ResearchDocumentService researchDocumentService;

    /** 종목별 뉴스·공시. {@code source} 를 빼면 원천을 섞어 최신순으로 내려준다. */
    @GetMapping("/{code}/documents")
    public ResearchDocumentListResponse documents(
            @PathVariable String code,
            @RequestParam(required = false) ResearchDocument.Source source,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return researchDocumentService.documents(code, source, cursor, size);
    }
}
