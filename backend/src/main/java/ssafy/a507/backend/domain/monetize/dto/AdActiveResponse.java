package ssafy.a507.backend.domain.monetize.dto;

import java.util.List;
import ssafy.a507.backend.domain.monetize.entity.AdBanner;

/** GET /api/v1/ads/active 200 응답. 메인 노출에 필요한 세 값만 내린다. */
public record AdActiveResponse(List<Item> items) {

    public record Item(Long id, String imageUrl, String linkUrl) {}

    public static AdActiveResponse of(List<AdBanner> banners) {
        return new AdActiveResponse(
                banners.stream()
                        .map(b -> new Item(b.getId(), b.getImageUrl(), b.getLinkUrl()))
                        .toList());
    }
}
