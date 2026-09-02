package ssafy.a507.backend.domain.chain.entity;

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

/**
 * 202 로 접수한 온체인 작업 한 건 (ANT-COMMUNITY-07).
 *
 * <p>202 를 쓰는 네 곳(예측 슬롯 초과 소각 · 구독 결제 · 광고 등록 · 시즌 참가)이 이 테이블
 * 하나를 공유한다. 리소스별로 폴링 창구를 두면 프론트가 상태값·조회 경로가 다른 폴링 코드를
 * 네 벌 갖게 되고, 광고는 PENDING 을 볼 수 있는 조회 API 가 아예 없다.
 *
 * <p>PK 가 UUID 인 것은 추측 방지다. 순차 int 였다면 남의 작업 id 를 하나씩 넣어 보며
 * 조회를 시도할 수 있다 — 403 을 돌려주더라도 "그 번호의 작업이 존재한다"는 사실이 샌다.
 */
@Entity
@Table(name = "operations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Operation {

    /** 202 를 쓰는 네 곳. {@code POST /wallet/nonce} 의 scope 어휘와 같다(온체인 전송이 없는 WALLET_LINK 만 빠진다). */
    public enum Kind {
        PREDICTION_BURN,
        SUBSCRIBE,
        AD,
        SEASON_JOIN
    }

    /** 전이는 체인 인덱서가 {@code chain_events} 기입과 함께 수행한다 — 서버에 폴링 배치가 없는 이유다. */
    public enum Status {
        PENDING,
        SUCCEEDED,
        FAILED
    }

    /** 성공 시 만들어졌거나 전이된 도메인 리소스. 대상별 FK 네 개 대신 타입+id 로 가리킨다. */
    public enum ResourceType {
        SUBSCRIPTION,
        AD,
        PREDICTION,
        SEASON_PARTICIPANT
    }

    /** {@code op_} + UUID = 39자. 명세 예시가 {@code op_8f3c…} 라 접두사를 값에 포함한다. */
    @Id
    @Column(length = 40)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 16)
    private ResourceType resourceType;

    /** {@link #resourceType} 과 한 쌍이다. 한쪽만 채워지면 리소스를 찾을 수 없다. */
    @Column(name = "resource_id")
    private Long resourceId;

    /** 접수 시 NULL 이고 전송 주체가 채운다. 인덱서는 이 값으로 이 행을 찾는다. */
    @Column(name = "tx_hash", length = 66)
    private String txHash;

    /** FAILED 일 때만. 어휘는 인덱서가 만든다 — 서버가 enum 으로 굳히면 값이 늘 때마다 배포가 필요하다. */
    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 종료 시각. NULL 이면 진행 중이고, 24h 정리 배치가 이 컬럼을 스캔한다. */
    @Column(name = "settled_at")
    private Instant settledAt;

    /**
     * 202 접수. 리소스는 접수 시점에 이미 PENDING 행으로 만들어 두므로 여기서 함께 받는다 —
     * 광고의 202 응답이 {@code adId} 를 함께 내려야 해서 나중에 채울 수가 없다.
     */
    public static Operation accept(User user, Kind kind, ResourceType resourceType, Long resourceId) {
        Operation operation = new Operation();
        operation.id = "op_" + UUID.randomUUID();
        operation.user = user;
        operation.kind = kind;
        operation.status = Status.PENDING;
        operation.resourceType = resourceType;
        operation.resourceId = resourceId;
        return operation;
    }

    /** 전송 주체가 tx 를 띄운 뒤 부른다. 인덱서가 tx_hash 로 이 행을 찾으므로 확정 전에 채워야 한다. */
    public void markSent(String txHash) {
        this.txHash = txHash;
    }

    /** 인덱서 전용. {@code chain_events} 기입과 같은 트랜잭션에서 부른다. */
    public void markSucceeded(String txHash) {
        this.txHash = txHash;
        this.status = Status.SUCCEEDED;
        this.settledAt = Instant.now();
    }

    /** 인덱서 전용. code 어휘는 인덱서가 정한다. */
    public void markFailed(String errorCode, String errorMessage) {
        this.status = Status.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.settledAt = Instant.now();
    }
}
