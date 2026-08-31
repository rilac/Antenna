package ssafy.a507.backend.domain.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.account.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 지갑은 계정당 1개다. UQ 위반을 기다리지 않고 먼저 걸러 낸다. */
    boolean existsByWalletAddress(String walletAddress);
}
