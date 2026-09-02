package ssafy.a507.backend.domain.monetize.service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.common.security.SignatureGuard;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.chain.entity.Operation;
import ssafy.a507.backend.domain.chain.repository.TokenLedgerRepository;
import ssafy.a507.backend.domain.chain.service.OperationService;
import ssafy.a507.backend.domain.monetize.dto.AdActiveResponse;
import ssafy.a507.backend.domain.monetize.dto.AdCreateRequest;
import ssafy.a507.backend.domain.monetize.dto.AdCreateResponse;
import ssafy.a507.backend.domain.monetize.entity.AdBanner;
import ssafy.a507.backend.domain.monetize.repository.AdBannerRepository;
import ssafy.a507.backend.domain.upload.entity.UploadFile;
import ssafy.a507.backend.domain.upload.repository.UploadFileRepository;

/** 스폰서드 배너 (ANT-COMMUNITY-05). 고정 단가 × 기간제 선착순이고 지불은 소각이다. */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AdProperties.class)
public class AdService {

    private static final List<AdBanner.Status> OCCUPYING =
            List.of(AdBanner.Status.PENDING, AdBanner.Status.ACTIVE);

    private final AdBannerRepository adBannerRepository;
    private final UploadFileRepository uploadFileRepository;
    private final UserRepository userRepository;
    private final TokenLedgerRepository tokenLedgerRepository;
    private final OperationService operationService;
    private final SignatureGuard signatureGuard;
    private final AdProperties properties;

    /**
     * 게재 신청. 검증 순서가 <b>기간 → 이미지 → 슬롯 → 잔액 → 서명</b> 인 것은 의도적이다.
     *
     * <p>{@link SignatureGuard#verify} 는 nonce 를 태운다. 서명을 먼저 검증하면 슬롯이 마감돼
     * 409 로 돌려보내는 요청에서도 nonce 가 사라져, 사용자는 지갑에서 다시 서명해야 한다.
     * 앞의 네 가지는 상태를 바꾸지 않는 판정이라 순서를 뒤로 미룰 이유가 없다.
     *
     * <p>거꾸로 서명 검증은 <b>행을 만들기 직전</b>이어야 한다. 검증 없이 만들면 남의 지갑으로
     * 결제되는 광고를 등록할 수 있다.
     */
    @Transactional
    public AdCreateResponse create(Long userId, AdCreateRequest request) {
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
        // ponytail: 세는 것과 만드는 것 사이가 열려 있어, 같은 순간에 들어온 두 신청이 둘 다
        // 슬롯을 통과할 수 있다. 제대로 막으려면 기간 겹침에 걸리는 배타 제약(Postgres 의
        // btree_gist + EXCLUDE)이나 슬롯 행 잠금이 필요하다. 자리가 하나뿐이고 신청이 하루
        // 몇 건인 단계라 넣지 않았다 — 실제로 부딪히면 EXCLUDE 제약이 가장 싸다.
        if (adBannerRepository.countByStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
                        OCCUPYING, endsAt, startsAt)
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
                                advertiser, image.getUrl(), request.linkUrl(), startsAt, endsAt));
        Operation operation =
                operationService.accept(
                        advertiser, Operation.Kind.AD, Operation.ResourceType.AD, banner.getId());

        return new AdCreateResponse(operation.getId(), banner.getId(), operation.getStatus());
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
}
