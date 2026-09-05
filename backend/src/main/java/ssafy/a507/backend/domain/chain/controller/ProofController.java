package ssafy.a507.backend.domain.chain.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.domain.chain.dto.ProofResponse;
import ssafy.a507.backend.domain.chain.service.ProofService;

/**
 * 3단계 검산 재료 (ANT-CHAIN-06). 경로는 {@code /predictions/{id}/proof} 지만 chain 도메인에 둔다 — 의존이 전부
 * 배치·머클(chain) 쪽이고, 예측 API(PRED-01) 가 자기 컨트롤러를 만들 때 이 서브리소스와 충돌하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
public class ProofController {

    private final ProofService proofService;

    @GetMapping("/{predictionId}/proof")
    public ProofResponse proof(@PathVariable long predictionId) {
        return proofService.proof(predictionId);
    }
}
