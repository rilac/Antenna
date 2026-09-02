package ssafy.a507.backend.domain.upload.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.common.idempotency.IdempotencyStore;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.upload.dto.UploadResponse;
import ssafy.a507.backend.domain.upload.entity.UploadFile;
import ssafy.a507.backend.domain.upload.service.UploadService;

/** 이미지 업로드 — ANT-COMMUNITY-06. 광고 배너 · 리포트 · 피드 첨부의 유일한 출처다. */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private static final String CREATE_ENDPOINT = "POST /uploads";

    private final UploadService uploadService;
    private final IdempotencyStore idempotencyStore;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 업로드. 명세 §1 의 멱등성 필수 대상 8개 중 하나다.
     *
     * <p>본문 해시는 파일 내용으로 만든다 — 파일명·크기만 쓰면 같은 이름·같은 크기의 다른
     * 이미지가 같은 요청으로 취급돼 엉뚱한 fileId 를 돌려주게 된다.
     *
     * <p>재요청에는 처음 만든 fileId 를 그대로 돌려주기 위해 그 값 하나만 저장한다. 응답의
     * 나머지 필드는 fileId 로 다시 만들 수 있다.
     */
    @PostMapping
    public ResponseEntity<UploadResponse> upload(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose) {

        Long userId = currentUserProvider.currentUserId();
        UploadFile.Purpose parsed = parsePurpose(purpose);
        byte[] content = read(file);

        String fileId = idempotencyStore.execute(
                userId,
                CREATE_ENDPOINT,
                idempotencyKey,
                IdempotencyStore.hash(parsed + ":" + IdempotencyStore.hash(content)),
                () -> uploadService.upload(userId, content, parsed).fileId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(uploadService.describe(fileId));
    }

    /**
     * 저장된 이미지. {@code upload_files.url} 이 가리키는 곳이다.
     *
     * <p>저장 위치가 로컬 볼륨이라 서버가 직접 내려보낸다. CDN 으로 옮기면 이 엔드포인트는
     * 사라지고 {@code url} 이 외부 주소를 담는다.
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> read(@PathVariable String fileId) {
        UploadService.StoredImage image = uploadService.load(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mime()))
                .body(image.content());
    }

    /**
     * 열거형 파라미터는 직접 파싱한다. 스프링의 자동 변환에 맡기면 어휘에 없는 값이 500 이 되고,
     * 오타는 클라이언트 잘못이므로 400 이어야 한다({@code ReportController} 와 같은 판단).
     */
    private UploadFile.Purpose parsePurpose(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "purpose");
        }
        try {
            return UploadFile.Purpose.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "purpose");
        }
    }

    /**
     * 파일 바이트를 한 번만 읽는다. 멱등 지문과 저장이 같은 배열을 쓴다 — Part 스트림을 두 번
     * 여는 것은 컨테이너 구현에 기대는 짓이고, 5MB 를 두 번 읽을 이유도 없다.
     */
    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "file");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
