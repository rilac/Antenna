package ssafy.a507.backend.domain.account;

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

    /** 화면 표시 이름이자 유일한 공개 식별자. */
    @Column(nullable = false, unique = true, length = 30)
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
}
