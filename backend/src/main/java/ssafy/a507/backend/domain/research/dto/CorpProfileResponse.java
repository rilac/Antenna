package ssafy.a507.backend.domain.research.dto;

import java.time.LocalDate;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.research.entity.CorpProfile;

/**
 * GET /api/v1/stocks/{code}/profile 200 응답 — 기업 개요 탭.
 *
 * <p>{@code industry} 는 DART 업종코드가 아니라 {@code stocks.sector}(KRX 분류)다. 탐색 화면의
 * 섹터 칩·경쟁사 비교가 같은 분류를 쓰므로 여기만 다른 체계를 내면 화면끼리 어긋난다.
 *
 * <p>{@code listedAt} 은 원천이 없어 항상 null 이다 — DART 기업개황에 상장일이 없다. 명세 키를
 * 지키기 위해 남겨 둔다.
 */
public record CorpProfileResponse(
        String corpName,
        String ceo,
        LocalDate establishedAt,
        LocalDate listedAt,
        String homepage,
        String address,
        String industry) {

    /** 기업개황이 아직 수집되지 않은 종목은 종목명만 채운다 — 200 + 빈 값 규칙. */
    public static CorpProfileResponse of(Stock stock, CorpProfile profile) {
        if (profile == null) {
            return new CorpProfileResponse(stock.getName(), null, null, null, null, null, stock.getSector());
        }
        return new CorpProfileResponse(
                profile.getCorpName(),
                profile.getCeoName(),
                profile.getEstablishedOn(),
                null,
                profile.getHomepageUrl(),
                profile.getAddress(),
                stock.getSector());
    }
}
