package ssafy.a507.backend.domain.account.entity;

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

/** 예측자 외부 채널. 피싱 차단을 위해 url은 https만 허용한다. */
@Entity
@Table(
        name = "user_channels",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_user_channels_user_url",
                        columnNames = {"user_id", "url"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserChannel {

    public enum Platform {
        YOUTUBE,
        BLOG,
        X,
        ETC
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Platform platform;

    @Column(nullable = false, length = 300)
    private String url;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
