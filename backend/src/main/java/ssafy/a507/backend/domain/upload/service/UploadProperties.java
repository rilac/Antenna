package ssafy.a507.backend.domain.upload.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 업로드 규격 (ANT-COMMUNITY-06).
 *
 * <p>설정이 비어 있어도 기본값으로 돈다 — 이 값들을 .env 에 넣지 않은 팀원의 로컬이 부팅부터
 * 실패하지 않게 하려는 것이다.
 *
 * @param maxBytes 파일 용량 상한. 명세는 5MB 이고 초과는 413 이다. 서블릿의 multipart 상한과
 *     같은 값이어야 한다 — 서블릿 쪽이 작으면 여기까지 오지 못하고 컨테이너가 먼저 끊는다.
 * @param adAspectRatio 배너 가로/세로 비율. <b>화면설계서로 확정되지 않은 값이라 조절 손잡이로
 *     남긴다.</b> 노출 자리가 고정이라 어긋나면 잘리거나 늘어나므로 올리는 시점에 막되, 실제
 *     시안이 나오면 이 값만 바꾼다.
 * @param adAspectTolerance 허용 오차. 사용자가 자른 이미지는 정수 픽셀이라 정확히 나누어
 *     떨어지지 않는다 — 0 으로 두면 눈으로 맞는 이미지가 전부 거절된다.
 */
@ConfigurationProperties(prefix = "app.uploads")
public record UploadProperties(Integer maxBytes, Double adAspectRatio, Double adAspectTolerance) {

    public UploadProperties {
        maxBytes = maxBytes == null ? 5 * 1024 * 1024 : maxBytes;
        adAspectRatio = adAspectRatio == null ? 4.0 : adAspectRatio;
        adAspectTolerance = adAspectTolerance == null ? 0.05 : adAspectTolerance;
    }
}
