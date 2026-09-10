package ssafy.a507.backend.domain.upload.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.community.entity.FeedPost;

/**
 * 업로드된 이미지 한 장 (ANT-COMMUNITY-06).
 *
 * <p>다른 API 는 이 행의 id 만 받고 URL 은 받지 않는다. 클라이언트가 넘긴 외부 URL 을 그대로
 * 저장하면 ① 서버가 프리뷰·렌더할 때 SSRF ② 이미지 URL 이 추적 픽셀로 쓰여 열람자 IP 가
 * 광고주에게 수집됨 ③ 승인 후 URL 내용만 바꿔치기가 열린다.
 *
 * <p>PK 가 UUID 인 것은 추측 방지다 — 순차 int 라면 남이 올린 이미지를 번호로 훑을 수 있다.
 * {@code operations.id} 와 달리 접두사가 없어 36자다.
 */
@Entity
@Table(name = "upload_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadFile {

    /** 용도. 규격 검증이 갈리는 유일한 값이다 — 배너(AD)만 비율이 고정이다. */
    public enum Purpose {
        AD,
        REPORT,
        POST
    }

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Purpose purpose;

    /** 저장 위치. 지금은 로컬 볼륨을 가리키는 내부 경로이고, CDN 전환 시 이 값만 바뀐다. */
    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false, length = 20)
    private String mime;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false)
    private int bytes;

    /** 업로드 시점엔 NULL 이고 {@code POST /posts} 의 imageFileIds 로 연결될 때 채운다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private FeedPost post;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static UploadFile create(
            User user, Purpose purpose, String mime, int width, int height, int bytes) {
        UploadFile file = new UploadFile();
        file.id = UUID.randomUUID().toString();
        file.user = user;
        file.purpose = purpose;
        file.mime = mime;
        file.width = width;
        file.height = height;
        file.bytes = bytes;
        return file;
    }

    /**
     * 파일 쓰기가 끝난 뒤 실제 위치를 채운다. {@code url} 이 NOT NULL 이라 행을 저장하기
     * 전에 불려야 한다.
     */
    public void locateAt(String url) {
        this.url = url;
    }

    public void attachTo(FeedPost post) {
        this.post = post;
    }
}
