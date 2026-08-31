package ssafy.a507.backend.domain.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {}

    /**
     * 닉네임 확정·변경 요청. 온보딩과 프로필 수정이 같은 API 를 쓴다.
     *
     * <p>공백만 있는 값과 앞뒤 공백을 막는다 — 화면에서 구분되지 않는 닉네임이 유일성만 통과하는 것을 피한다.
     */
    public record NicknameRequest(
            @NotBlank
                    @Size(min = 2, max = 30)
                    @Pattern(regexp = "\\S(.*\\S)?", message = "닉네임 앞뒤에 공백을 둘 수 없습니다.")
                    String nickname) {}

    public record NicknameAvailability(boolean available) {}

    /**
     * 세션 복원·프로필 화면이 쓰는 최소 프로필. 관심 섹터·채널은 회원 API 스토리에서 채운다.
     *
     * <p>온보딩 완료 여부는 따로 내려보내지 않는다 — 닉네임과 지갑 연동이 각각 어느 단계까지 왔는지
     * 이미 담겨 있고, 어느 단계를 막을지는 화면이 정한다.
     */
    public record MeResponse(
            Long id,
            String nickname,
            String introduce,
            String walletAddress,
            boolean walletLinked,
            String role) {}
}
