package ssafy.a507.backend.domain.chain.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.chain.dto.AnchorDetailResponse;
import ssafy.a507.backend.domain.chain.dto.AnchorListResponse;
import ssafy.a507.backend.domain.chain.service.AnchorQueryService;

/** 커밋 원장 — 앵커 배치 목록·상세 (ANT-CHAIN-06). 회원 전용이라 사용자 id 를 읽어 인증만 확인한다. */
@RestController
@RequestMapping("/api/v1/anchors")
@RequiredArgsConstructor
public class AnchorQueryController {

    private final AnchorQueryService anchorQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public AnchorListResponse list(
            @RequestParam(required = false) Long cursor, @RequestParam(required = false) Integer size) {
        currentUserProvider.currentUserId();
        return anchorQueryService.list(cursor, size);
    }

    @GetMapping("/{id}")
    public AnchorDetailResponse detail(@PathVariable long id) {
        currentUserProvider.currentUserId();
        return anchorQueryService.detail(id);
    }
}
