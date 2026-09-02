package ssafy.a507.backend.domain.upload.dto;

import ssafy.a507.backend.domain.upload.entity.UploadFile;

/**
 * POST /api/v1/uploads 201 응답.
 *
 * <p>다른 API 에 넘기는 값은 {@code fileId} 하나다. {@code url} 은 화면에 미리보기를 그리기
 * 위한 것이고, 되돌려 보내도 서버는 받지 않는다.
 */
public record UploadResponse(
        String fileId, String url, int width, int height, int bytes, String mime) {

    public static UploadResponse from(UploadFile file) {
        return new UploadResponse(
                file.getId(),
                file.getUrl(),
                file.getWidth(),
                file.getHeight(),
                file.getBytes(),
                file.getMime());
    }
}
