package ssafy.a507.backend.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 계정. 모든 도메인이 이 id로 회원을 참조한다. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public enum Role {
        USER,
        ADMIN
    }

    /** 영구 차단만 둔다. BANNED면 로그인을 거부하되 기존 예측·원장은 남긴다. */
    public enum Status {
        ACTIVE,
        BANNED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 화면 표시 이름이자 유일한 공개 식별자.
     *
     * <p>NULL 은 "온보딩에서 아직 정하지 않음"을 뜻한다. 임시 닉네임을 넣어 두면 온보딩을 중간에
     * 그만둔 회원을 다음 로그인에서 다시 온보딩으로 보낼 수 없다.
     */
    @Column(unique = true, length = 30)
    private String nickname;

    /** 프로필 소개 문구. 구독 판단 근거로 쓰인다. */
    @Column(length = 200)
    private String introduce;

    /** SSAFY WALLET 연동 주소. 소문자로 정규화하며 NULL이면 예측을 등록할 수 없다. */
    @Column(name = "wallet_address", unique = true, length = 42)
    private String walletAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** OAuth 최초 로그인 시 생성. 닉네임은 온보딩에서 정하므로 아직 비어 있다. */
    public static User create() {
        User user = new User();
        user.role = Role.USER;
        user.status = Status.ACTIVE;
        return user;
    }

    /** 온보딩·프로필 수정에서 닉네임을 확정한다. 중복 검사는 서비스가 먼저 한다. */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** false 면 온보딩을 마치지 않은 회원이다. */
    public boolean hasNickname() {
        return nickname != null;
    }
}
