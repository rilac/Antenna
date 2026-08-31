package ssafy.a507.backend.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.dto.UserDtos.MeResponse;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        return toResponse(find(userId));
    }

    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        return !users.existsByNickname(nickname);
    }

    /** 온보딩의 닉네임 확정과 이후 변경이 같은 경로를 쓴다. */
    @Transactional
    public MeResponse changeNickname(Long userId, String nickname) {
        User user = find(userId);
        if (nickname.equals(user.getNickname())) {
            return toResponse(user);
        }
        if (users.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.changeNickname(nickname);
        try {
            users.flush();
        } catch (DataIntegrityViolationException e) {
            // 위 검사와 flush 사이에 같은 닉네임이 들어온 경우. UQ 가 최종 판정자다.
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return toResponse(user);
    }

    private User find(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private MeResponse toResponse(User user) {
        return new MeResponse(
                user.getId(),
                user.getNickname(),
                user.getIntroduce(),
                user.getWalletAddress(),
                user.getWalletAddress() != null,
                user.getRole().name());
    }
}
