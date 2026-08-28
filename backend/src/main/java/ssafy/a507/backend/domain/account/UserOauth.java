package ssafy.a507.backend.domain.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** OAuth 연동. 한 계정에 구글·SSAFY를 동시에 붙일 수 있다. */
@Entity
@Table(
        name = "user_oauth",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_user_oauth_provider_user",
                        columnNames = {"provider", "provider_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOauth {

    public enum Provider {
        GOOGLE,
        SSAFY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Provider provider;

    /** 프로바이더가 발급한 불변 ID. 로그인 매칭 키는 이메일이 아니라 이 값이다. */
    @Column(name = "provider_user_id", nullable = false, length = 128)
    private String providerUserId;

    /** 표시용. 프로바이더가 검증했다는 보장이 없어 계정 자동 병합에 쓰지 않는다. */
    @Column(length = 255)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
