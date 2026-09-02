package ssafy.a507.backend.domain.monetize.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.idempotency.IdempotencyStore;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.chain.entity.Operation;
import ssafy.a507.backend.domain.monetize.dto.AdActiveResponse;
import ssafy.a507.backend.domain.monetize.dto.AdCreateRequest;
import ssafy.a507.backend.domain.monetize.dto.AdCreateResponse;
import ssafy.a507.backend.domain.monetize.service.AdService;

/** 스폰서드 광고 — ANT-COMMUNITY-05. */
@RestController
@RequestMapping("/api/v1/ads")
@RequiredArgsConstructor
public class AdController {

    private static final String CREATE_ENDPOINT = "POST /ads";

    private final AdService adService;
    private final IdempotencyStore idempotencyStore;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 게재 신청 — 202. 명세 §1 의 멱등성 필수 대상이다.
     *
     * <p>토큰이 소각되는 요청이라 되돌릴 수단이 없다. 타임아웃 뒤 한 번의 재시도로 게재료가
     * 두 번 나가면 복구 경로가 없으므로, 재요청에는 처음 만든 작업을 그대로 돌려준다.
     *
     * <p>멱등 저장소에는 {@code operationId:adId} 두 값만 넣는다 — 상태는 항상 PENDING 이라
     * 저장할 필요가 없고, 저장한 시점의 상태를 돌려주면 오히려 낡은 값이 된다.
     */
    @PostMapping
    public ResponseEntity<AdCreateResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AdCreateRequest request) {

        Long userId = currentUserProvider.currentUserId();
        String accepted = idempotencyStore.execute(
                userId,
                CREATE_ENDPOINT,
                idempotencyKey,
                IdempotencyStore.hash(request.toString()),
                () -> {
                    AdCreateResponse created = adService.create(userId, request);
                    return created.operationId() + ":" + created.adId();
                });

        int at = accepted.lastIndexOf(':');
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AdCreateResponse(
                        accepted.substring(0, at),
                        Long.valueOf(accepted.substring(at + 1)),
                        Operation.Status.PENDING));
    }

    @GetMapping("/active")
    public AdActiveResponse active() {
        return adService.active();
    }
}
