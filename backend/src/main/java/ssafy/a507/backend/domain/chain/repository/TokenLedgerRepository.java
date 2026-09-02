package ssafy.a507.backend.domain.chain.repository;

import java.math.BigInteger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.a507.backend.domain.chain.entity.TokenLedger;

public interface TokenLedgerRepository extends JpaRepository<TokenLedger, Long> {

    /**
     * 잔액. 원장이 append-only 라 SUM(delta) 가 곧 잔액이다.
     *
     * <p>행이 하나도 없으면 SUM 이 NULL 이라 {@link java.util.Optional} 로 받는다 —
     * 원시 타입으로 받으면 신규 회원 조회에서 NPE 가 난다.
     */
    @Query("select sum(l.delta) from TokenLedger l where l.user.id = :userId")
    java.util.Optional<BigInteger> sumDeltaByUserId(@Param("userId") Long userId);

    default BigInteger balanceOf(Long userId) {
        return sumDeltaByUserId(userId).orElse(BigInteger.ZERO);
    }
}
