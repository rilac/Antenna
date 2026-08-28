package ssafy.a507.backend.domain.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.account.entity.UserOauth;

public interface UserOauthRepository extends JpaRepository<UserOauth, Long> {}
