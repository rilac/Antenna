package ssafy.a507.backend.domain.upload.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.upload.dto.UploadResponse;
import ssafy.a507.backend.domain.upload.entity.UploadFile;
import ssafy.a507.backend.domain.upload.repository.UploadFileRepository;

/** 이미지 업로드 · 조회 (ANT-COMMUNITY-06). 배너·리포트·피드 첨부의 유일한 출처다. */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(UploadProperties.class)
public class UploadService {

    private final UploadFileRepository uploadFileRepository;
    private final UserRepository userRepository;
    private final FileStorage storage;
    private final UploadProperties properties;

    /**
     * 업로드. 바이트는 컨트롤러가 이미 읽어 넘긴다 — 멱등 지문에 한 번, 저장에 한 번 읽으면
     * 5MB 를 두 번 읽는 데다 Part 스트림을 두 번 여는 것이 컨테이너 구현에 기댄다.
     *
     * <p>검증 순서가 용량 → 형식 → 용도별 규격인 것은 싼 판정을 먼저 돌리기 위해서다.
     *
     * <p>파일은 행을 먼저 만든 뒤에 쓴다. id 가 저장 경로이자 조회 URL 이라 순서를 뒤집을 수
     * 없고, 쓰기가 실패하면 트랜잭션이 되돌아 행도 남지 않는다.
     *
     * <p>ponytail: 반대 방향의 고아(행은 롤백됐는데 파일은 남는 경우)는 정리하지 않는다.
     * 커밋 실패에서만 생기고 UUID 이름이라 충돌하지 않으며, 실제로 쌓이면 참조 없는 파일을
     * 지우는 배치 하나로 끝난다.
     */
    @Transactional
    public UploadResponse upload(Long userId, byte[] content, UploadFile.Purpose purpose) {
        if (content.length > properties.maxBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "file");
        }

        ImageProbe.Image image = ImageProbe.probe(content, properties.maxPixels());
        if (purpose == UploadFile.Purpose.AD) {
            validateBannerRatio(image);
        }

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        UploadFile saved =
                uploadFileRepository.save(
                        UploadFile.create(
                                user,
                                purpose,
                                image.mime(),
                                image.width(),
                                image.height(),
                                content.length));
        saved.locateAt(storage.store(saved.getId(), content));
        return UploadResponse.from(saved);
    }

    /**
     * 응답 재구성. 멱등 저장소에는 fileId 하나만 넣으므로, 재요청이든 최초 요청이든 응답은
     * 여기서 같은 방식으로 만든다 — 두 경로가 다른 코드를 타면 재요청 응답만 조용히 어긋난다.
     */
    @Transactional(readOnly = true)
    public UploadResponse describe(String fileId) {
        return UploadResponse.from(
                uploadFileRepository
                        .findById(fileId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_FILE_NOT_FOUND)));
    }

    /** 조회. 로그인한 사용자면 누구나 받을 수 있다 — 배너와 리포트 이미지는 원래 남에게 보이는 것이다. */
    @Transactional(readOnly = true)
    public StoredImage load(String fileId) {
        UploadFile file =
                uploadFileRepository
                        .findById(fileId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_FILE_NOT_FOUND));
        return new StoredImage(file.getMime(), storage.read(fileId));
    }

    public record StoredImage(String mime, byte[] content) {}

    /**
     * 배너는 노출 자리가 고정 비율이다. 어긋난 이미지를 받아 두면 화면에서 잘리거나 늘어나는데,
     * 그때는 이미 광고비를 받은 뒤라 되돌릴 수단이 없다.
     */
    private void validateBannerRatio(ImageProbe.Image image) {
        double ratio = (double) image.width() / image.height();
        if (Math.abs(ratio - properties.adAspectRatio()) > properties.adAspectTolerance()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_RATIO, "file");
        }
    }

}
