package ssafy.a507.backend.domain.monetize.dto;

/**
 * GET /api/v1/ads/pricing 200 응답 (ANT-COMMUNITY-08).
 *
 * <p>화면이 서명 전에 비용을 보여주는 근거다. 이 값을 내려주는 곳이 없던 동안 프론트는 같은
 * 수를 상수로 복제해 뒀는데({@code frontend/src/api/ads.ts}), 서명 문자열에 가격이 들어가지
 * 않아(imageFileId · linkUrl · days · chainId · nonce) 단가를 바꾸면 화면 금액과 실제 차감액이
 * 달라도 서명은 통과한다. 소각은 되돌릴 수단이 없으므로 단가는 서버가 말해야 한다.
 *
 * <p>{@code pricePerDayWei} 만 문자열이다. ANT 는 decimals 0 이라(ANT-CHAIN-03) {@code "1"} 이
 * 곧 1 ANT 이고 18자리가 아니지만, 금액 필드는 형식을 문자열로 통일한다 — 프론트가 잔액·수수료를
 * 이미 문자열로 받는다. 이름의 wei 도 그 통일에서 온 것이고 10^18 을 곱한 값이 아니다.
 */
public record AdPricingResponse(
        String pricePerDayWei, int minDays, int maxDays, int slotCount) {}
