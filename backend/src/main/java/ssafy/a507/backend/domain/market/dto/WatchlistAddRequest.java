package ssafy.a507.backend.domain.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /api/v1/watchlist 요청 본문. 종목코드는 6자리 숫자 — 형식이 틀리면 조회 전에 400 이다. */
public record WatchlistAddRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "종목코드는 6자리 숫자입니다.") String stockCode) {}
