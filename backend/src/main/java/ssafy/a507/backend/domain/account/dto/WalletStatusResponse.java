package ssafy.a507.backend.domain.account.dto;

/** 미연동이면 walletAddress가 null로 내려간다 — 프론트가 분기하기 쉽게 필드를 지우지 않는다. */
public record WalletStatusResponse(boolean linked, String walletAddress) {}
