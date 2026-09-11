package ssafy.a507.backend.domain.monetize.service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.common.security.SignatureGuard;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.chain.entity.Operation;
import ssafy.a507.backend.domain.chain.relay.TokenReason;
import ssafy.a507.backend.domain.chain.repository.TokenLedgerRepository;
import ssafy.a507.backend.domain.chain.service.OperationService;
import ssafy.a507.backend.domain.chain.service.TokenOperationService;
import ssafy.a507.backend.domain.monetize.dto.AdActiveResponse;
import ssafy.a507.backend.domain.monetize.dto.AdCreateRequest;
import ssafy.a507.backend.domain.monetize.dto.AdCreateResponse;
import ssafy.a507.backend.domain.monetize.dto.AdPricingResponse;
import ssafy.a507.backend.domain.monetize.entity.AdBanner;
import ssafy.a507.backend.domain.monetize.repository.AdBannerRepository;
import ssafy.a507.backend.domain.upload.entity.UploadFile;
import ssafy.a507.backend.domain.upload.repository.UploadFileRepository;

/** 스폰서드 배너 (ANT-COMMUNITY-05). 고정 단가 × 기간제 선착순이고 지불은 소각이다. */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AdProperties.class)
public class AdService {

    /**
     * 최소 게재 기간. {@link AdProperties} 에 두지 않은 것은 하루 미만을 막는 곳이
     * {@link AdCreateRequest} 의 {@code @Min(1)} 이고, 애노테이션 인자는 컴파일 상수라
     * 설정으로 뺄 수 없기 때문이다. 그 1 과 같은 값을 조건 조회에서 내린다.
     */
    private static final int MIN_DAYS = 1;

    private final AdBannerRepository adBannerRepository;
    private final UploadFileRepository uploadFileRepository;
    private final UserRepository userRepository;
    private final TokenLedgerRepository tokenLedgerRepository;
    private final OperationService operationService;
    private final SignatureGuard signatureGuard;
    private final AdProperties properties;
    private final TokenOperationService tokenOperationService;
    private final TransactionTemplate tx;

    /**
     * 게재 신청 — 접수하고 게재료 소각 tx 를 보낸다. 확정(배너 ACTIVE)은 토큰 인덱서가 {@code Burned} 이벤트를 보고 한다
     * (ANT-CHAIN-11). 이 메서드는 202 까지다.
     *
     * <p><b>트랜잭션을 손으로 나눈다</b> — {@link TokenOperationService} 의 호출자 계약(ANT-CHAIN-12):
     * ① 체인이 꺼져 있으면 아무것도 쓰기 전에 503 ② 접수(검사 · 서명 · 배너 PENDING · 작업)를 커밋
     * ③ 트랜잭션 밖에서 소각 전송. DB 커밋이 먼저인 이유는 반대로 하면 "게재료는 탔는데 광고가 없는" 상태가 생기고
     * 그건 되돌릴 수단이 없어서다. 그래서 이 메서드에는 {@code @Transactional} 이 없다 — 붙이면 ③ 이 거부된다.
     *
     * <p>③ 이 실패하면(체인 잔액 부족 409 · RPC 503) 작업은 {@link TokenOperationService} 가 FAILED 로 닫고, 배너는 여기서
     * REJECTED 로 닫는다. 안 닫으면 거절된 신청이 유예 창({@code pending-grace-minutes}) 동안 자리를 막는다. 인덱서가
     * 이벤트 없는 실패를 닫을 때 {@link AdBanner#reject()} 를 부르는 것과 같은 규칙이다.
     */
    public AdCreateResponse create(Long userId, AdCreateRequest request) {
        tokenOperationService.requireEnabled();
        Accepted accepted = tx.execute(status -> accept(userId, request));
        try {
            tokenOperationService.dispatchBurn(accepted.operationId(), accepted.price(), TokenReason.AD_PAY);
        } catch (RuntimeException e) {
            tx.executeWithoutResult(
                    status -> adBannerRepository.findById(accepted.adId()).ifPresent(AdBanner::reject));
            throw e;
        }
        // 전송 뒤에도 작업은 PENDING 이다 — tx 해시만 붙었고 확정은 인덱서 몫이다.
        return new AdCreateResponse(accepted.operationId(), accepted.adId(), Operation.Status.PENDING);
    }

    /**
     * 접수. 검증 순서가 <b>기간 → 이미지 → 슬롯 → 잔액 → 서명</b> 인 것은 의도적이다.
     *
     * <p>{@link SignatureGuard#verify} 는 nonce 를 태운다. 서명을 먼저 검증하면 슬롯이 마감돼
     * 409 로 돌려보내는 요청에서도 nonce 가 사라져, 사용자는 지갑에서 다시 서명해야 한다.
     * 앞의 네 가지는 상태를 바꾸지 않는 판정이라 순서를 뒤로 미룰 이유가 없다.
     *
     * <p>거꾸로 서명 검증은 <b>행을 만들기 직전</b>이어야 한다. 검증 없이 만들면 남의 지갑으로
     * 결제되는 광고를 등록할 수 있다.
     *
     * <p>잔액 검사는 원장(token_ledger) 기준의 1차 거름이다. 체인 잔액은 소각 전송의 시뮬레이션이 한 번 더 본다.
     */
    private Accepted accept(Long userId, AdCreateRequest request) {
        if (request.days() > properties.maxDays()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "days");
        }

        UploadFile image =
                uploadFileRepository
                        .findByIdAndUserId(request.imageFileId(), userId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.UPLOAD_FILE_NOT_FOUND, "imageFileId"));
        if (image.getPurpose() != UploadFile.Purpose.AD) {
            // 배너 비율 검증은 purpose=AD 로 올릴 때만 돈다. 다른 용도로 올린 파일을 여기서
            // 받아 주면 그 검증을 건너뛴 이미지가 배너 자리에 걸린다.
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "imageFileId");
        }

        // 게재 시작을 고를 수 없다 — 요청 본문에 기간(days)만 있고 시작일이 없다.
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plus(request.days(), ChronoUnit.DAYS);
        // 확정을 기다리다 시간이 지난 신청은 자리를 놓은 것으로 본다 — 접수가 토큰을 차감하지
        // 않으므로, 그러지 않으면 잔액만 들고 신청만 해 두는 계정이 공짜로 자리를 막는다.
        Instant pendingSince = startsAt.minus(properties.pendingGraceMinutes(), ChronoUnit.MINUTES);
        // ponytail: 세는 것과 만드는 것 사이가 열려 있어, 같은 순간에 들어온 두 신청이 둘 다
        // 슬롯을 통과할 수 있다. 제대로 막으려면 기간 겹침에 걸리는 배타 제약(Postgres 의
        // btree_gist + EXCLUDE)이나 슬롯 행 잠금이 필요하다. 자리가 하나뿐이고 신청이 하루
        // 몇 건인 단계라 넣지 않았다 — 실제로 부딪히면 EXCLUDE 제약이 가장 싸다.
        if (adBannerRepository.countOccupying(
                        AdBanner.Status.ACTIVE,
                        AdBanner.Status.PENDING,
                        startsAt,
                        endsAt,
                        pendingSince)
                >= properties.slotCount()) {
            throw new BusinessException(ErrorCode.AD_SLOT_SOLD_OUT);
        }

        BigInteger price = properties.priceFor(request.days());
        if (tokenLedgerRepository.balanceOf(userId).compareTo(price) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        User advertiser =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        signatureGuard.verify(userId, request);

        AdBanner banner =
                adBannerRepository.save(
                        AdBanner.request(
                                advertiser,
                                image.getUrl(),
                                request.linkUrl(),
                                startsAt,
                                endsAt,
                                price));
        Operation operation =
                operationService.accept(
                        advertiser, Operation.Kind.AD, Operation.ResourceType.AD, banner.getId());

        return new Accepted(operation.getId(), banner.getId(), price);
    }

    /**
     * 게재 조건. 설정값을 그대로 내리고 상태는 읽지 않는다 — 화면이 서명 전에 비용을 계산할
     * 근거이고, 이 값이 없으면 프론트가 단가를 복제해 두는 수밖에 없다.
     */
    public AdPricingResponse pricing() {
        return new AdPricingResponse(
                properties.pricePerDayWei().toString(),
                MIN_DAYS,
                properties.maxDays(),
                properties.slotCount());
    }

    /** 메인 노출용. 상태와 기간을 함께 보므로 만료 배치가 없어도 끝난 광고는 빠진다. */
    @Transactional(readOnly = true)
    public AdActiveResponse active() {
        Instant now = Instant.now();
        return AdActiveResponse.of(
                adBannerRepository
                        .findByStatusAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtAsc(
                                AdBanner.Status.ACTIVE, now, now));
    }

    /** 접수가 커밋한 것 중 소각 전송에 필요한 셋. */
    private record Accepted(String operationId, Long adId, BigInteger price) {}
}
