package ssafy.a507.backend.domain.monetize.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.monetize.entity.AdBanner;

public interface AdBannerRepository extends JpaRepository<AdBanner, Long> {

    /**
     * 지금 노출할 배너. 상태와 기간을 함께 본다 — ACTIVE 로 두고 만료만 지난 행이 남으므로
     * 상태만 보면 끝난 광고가 계속 뜬다. 상태를 ENDED 로 바꾸는 배치가 없어도 정확하다.
     */
    List<AdBanner> findByStatusAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtAsc(
            AdBanner.Status status, Instant at, Instant sameAt);

    /**
     * 요청 기간과 겹치는 신청 수. 선착순 슬롯 판정에 쓴다.
     *
     * <p>PENDING 도 함께 센다. 결제 확정을 기다리는 신청을 비어 있는 것으로 보면, 확정되는
     * 순간 같은 기간에 배너가 두 개가 되고 자리는 하나뿐이다.
     *
     * <p>겹침 판정이 {@code startsAt < 요청 끝} 과 {@code endsAt > 요청 시작} 인 것은 반열린
     * 구간 규칙이다 — 한 배너가 끝나는 순간 다음 배너가 시작하는 경우를 겹침으로 세지 않는다.
     */
    long countByStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
            Collection<AdBanner.Status> statuses, Instant endsAt, Instant startsAt);
}
