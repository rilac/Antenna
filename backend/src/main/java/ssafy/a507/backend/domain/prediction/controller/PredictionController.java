package ssafy.a507.backend.domain.prediction.controller;

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
import ssafy.a507.backend.domain.prediction.dto.PredictionCreateRequest;
import ssafy.a507.backend.domain.prediction.dto.PredictionCreateResponse;
import ssafy.a507.backend.domain.prediction.dto.PredictionSlotResponse;
import ssafy.a507.backend.domain.prediction.service.PredictionRegisterService;

/** 예측 등록·슬롯 (ANT-PRED-01). 내 예측 목록({@code /me})은 {@code MyPredictionController} — 같은 base path 를 나눠 쓴다. */
@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private static final String CREATE_ENDPOINT = "POST /predictions";

    private final PredictionRegisterService registerService;
    private final IdempotencyStore idempotencyStore;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 예측 등록(봉인). 명세 §1 멱등성 필수 대상이라 {@code Idempotency-Key} 를 받는다 — 예측에는 삭제 API 가 없고 슬롯은
     * 되돌아오지 않는다. 타임아웃 뒤 같은 키로 재시도하면 처음 만든 예측의 id 로 같은 201 을 다시 만든다(서명 검증도 다시
     * 타지 않으니 nonce 가 이미 사라졌어도 된다). 저장하는 건 {@code PostController} 와 같이 id 하나뿐이다.
     */
    @PostMapping
    public ResponseEntity<PredictionCreateResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PredictionCreateRequest request) {
        Long userId = currentUserProvider.currentUserId();
        String id = idempotencyStore.execute(
                userId,
                CREATE_ENDPOINT,
                idempotencyKey,
                // record 의 toString 은 모든 구성요소(서명·noteSalt 포함)를 순서대로 담는다 — 같은 본문이면 같은 해시.
                IdempotencyStore.hash(request.toString()),
                () -> String.valueOf(registerService.register(userId, request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(registerService.created(userId, Long.parseLong(id)));
    }

    /** 오늘 무료 슬롯. 등록 화면이 서명 전에 부른다. */
    @GetMapping("/slots")
    public PredictionSlotResponse slots() {
        return registerService.slots(currentUserProvider.currentUserId());
    }
}
