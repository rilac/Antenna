package ssafy.a507.backend.domain.upload.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.upload.entity.UploadFile;

public interface UploadFileRepository extends JpaRepository<UploadFile, String> {

    /**
     * 업로더 본인 것만 찾는다. 다른 API 가 fileId 를 넘겨받을 때 쓰는 조회라, 소유자 조건이
     * 없으면 남이 올린 이미지의 id 를 알아내 자기 광고 배너로 쓸 수 있다.
     */
    Optional<UploadFile> findByIdAndUserId(String id, Long userId);
}
