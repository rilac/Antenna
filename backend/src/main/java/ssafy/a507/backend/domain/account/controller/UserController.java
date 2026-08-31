package ssafy.a507.backend.domain.account.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.account.dto.UserDtos.MeResponse;
import ssafy.a507.backend.domain.account.dto.UserDtos.NicknameAvailability;
import ssafy.a507.backend.domain.account.dto.UserDtos.NicknameRequest;
import ssafy.a507.backend.domain.account.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUser;

    /** 세션 복원용. 새로고침 후 refresh 로 access 를 되찾은 프론트가 곧바로 부른다. */
    @GetMapping("/me")
    public MeResponse me() {
        return userService.me(currentUser.currentUserId());
    }

    /** 온보딩 입력 중 실시간 중복 검사. */
    @GetMapping("/nickname/availability")
    public NicknameAvailability availability(
            @RequestParam @NotBlank @Size(min = 2, max = 30) String nickname) {
        return new NicknameAvailability(userService.isNicknameAvailable(nickname));
    }

    /** 온보딩의 닉네임 확정. 명세 §회원 의 프로필 수정과 같은 자리이며 지금은 닉네임만 받는다. */
    @PatchMapping("/me")
    public MeResponse updateMe(@Valid @RequestBody NicknameRequest request) {
        return userService.changeNickname(currentUser.currentUserId(), request.nickname());
    }
}
