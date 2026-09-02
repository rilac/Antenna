package ssafy.a507.backend.domain.chain.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.chain.dto.OperationResponse;
import ssafy.a507.backend.domain.chain.service.OperationService;

/** 202 온체인 작업의 단일 폴링 창구 — ANT-COMMUNITY-07. */
@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationController {

    private final OperationService operationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/{operationId}")
    public OperationResponse find(@PathVariable String operationId) {
        return operationService.find(currentUserProvider.currentUserId(), operationId);
    }
}
