package ssafy.a507.backend.domain.account.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.account.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    /** 지갑은 계정당 1개다. UQ 위반을 기다리지 않고 먼저 걸러 낸다. */
    boolean existsByWalletAddress(String walletAddress);

    /** 인덱서 ②(ANT-CHAIN-11)가 이벤트의 지갑 주소(소문자)로 회원을 찾는다. 없으면 회원이 아닌 지갑 — 원장에 안 쓴다. */
    Optional<User> findByWalletAddress(String walletAddress);
}
