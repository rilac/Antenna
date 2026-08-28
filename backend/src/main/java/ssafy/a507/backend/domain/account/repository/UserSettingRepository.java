package ssafy.a507.backend.domain.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.account.entity.UserSetting;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {}
