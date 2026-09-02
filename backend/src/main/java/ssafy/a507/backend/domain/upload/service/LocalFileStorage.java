package ssafy.a507.backend.domain.upload.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;

/**
 * 로컬 볼륨 저장 (ANT-COMMUNITY-06).
 *
 * <p>1차 결정이다 — 팀 규모에서 오브젝트 스토리지 자격증명·버킷 정책을 세우는 비용이
 * 볼륨 하나보다 크다. 컨테이너에서는 이 경로를 볼륨으로 마운트해야 재배포에 파일이 날아가지
 * 않는다. S3 로 옮길 때 바뀌는 파일은 이것 하나다.
 *
 * <p>확장자를 붙이지 않는다. 형식은 {@code upload_files.mime} 이 갖고 있고, 파일명에 확장자가
 * 있으면 정적 서빙이 열렸을 때 그 이름으로 실행·해석될 여지가 생긴다.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final String baseUrl;

    public LocalFileStorage(
            @Value("${app.uploads.dir:./uploads}") String dir,
            @Value("${app.uploads.base-url:/api/v1/uploads}") String baseUrl) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
    }

    @Override
    public String store(String fileId, byte[] content) {
        try {
            Files.createDirectories(root);
            Files.write(resolve(fileId), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baseUrl + "/" + fileId;
    }

    @Override
    public byte[] read(String fileId) {
        Path path = resolve(fileId);
        if (!Files.isRegularFile(path)) {
            // DB 에는 행이 있는데 파일이 없다 — 볼륨을 마운트하지 않고 재배포한 경우다.
            throw new BusinessException(ErrorCode.UPLOAD_FILE_NOT_FOUND);
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * id 는 우리가 만든 UUID 라서 경로 조작이 들어올 자리가 없지만, 조회는 경로 변수를 받는다.
     * 정규화한 결과가 root 바로 아래인지 확인해 {@code ../} 가 섞인 값을 걸러낸다.
     */
    private Path resolve(String fileId) {
        Path path = root.resolve(fileId).normalize();
        if (!root.equals(path.getParent())) {
            throw new BusinessException(ErrorCode.UPLOAD_FILE_NOT_FOUND);
        }
        return path;
    }
}
