package ssafy.a507.backend.domain.monetize.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.monetize.entity.AdBanner;

public interface AdBannerRepository extends JpaRepository<AdBanner, Long> {

    /**
     * 지금 노출할 배너. 상태와 기간을 함께 본다 — ACTIVE 로 두고 만료만 지난 행이 남으므로
     * 상태만 보면 끝난 광고가 계속 뜬다. 상태를 ENDED 로 바꾸는 배치가 없어도 정확하다.
     */
    List<AdBanner> findByStatusAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtAsc(
            AdBanner.Status status, Instant at, Instant sameAt);

    /**
     * 요청 기간에서 자리를 차지하고 있는 신청 수. 선착순 슬롯 판정에 쓴다.
     *
     * <p>PENDING 도 센다. 결제 확정을 기다리는 신청을 비어 있는 것으로 보면, 확정되는 순간
     * 같은 기간에 배너가 두 개가 되고 자리는 하나뿐이다.
     *
     * <p><b>단, 오래된 PENDING 은 세지 않는다.</b> 접수는 토큰을 실제로 차감하지 않는다 —
     * 소각은 나중에 오는 온체인 tx 다. 그래서 잔액만 들고 있는 계정이 신청만 해 두고 tx 를
     * 영영 보내지 않으면, 아무 비용 없이 그 기간의 자리를 통째로 막을 수 있다. 확정에 걸리는
     * 시간(grace) 이 지난 PENDING 은 자리를 놓은 것으로 본다.
     *
     * <p>겹침 판정이 {@code startsAt < 요청 끝} 과 {@code endsAt > 요청 시작} 인 것은 반열린
     * 구간 규칙이다 — 한 배너가 끝나는 순간 다음 배너가 시작하는 경우를 겹침으로 세지 않는다.
     */
    @Query("""
            select count(b) from AdBanner b
            where b.startsAt < :endsAt and b.endsAt > :startsAt
              and (b.status = :active
                   or (b.status = :pending and b.createdAt > :pendingSince))
            """)
    long countOccupying(
            @Param("active") AdBanner.Status active,
            @Param("pending") AdBanner.Status pending,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("pendingSince") Instant pendingSince);
}
