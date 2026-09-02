package ssafy.a507.backend.domain.market.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * 공공데이터포털 「금융위원회_주식시세정보」 설정.
 *
 * <p>주소는 문서로 확정된 값이라 환경마다 달라지지 않는다 — application.yaml 에 상수로 두고
 * .env 로는 인증키만 받는다(SSAFY SSO 와 같은 규칙).
 *
 * @param serviceKey 포털이 발급한 <b>Decoding</b> 인증키 · 비어 있으면 수집을 아예 돌리지 않는다
 * @param baseUrl 서비스 기본 주소
 * @param pageSize 한 번에 받을 행 수(numOfRows) · 종목 수가 3천 안팎이라 3콜이면 하루가 끝난다
 * @param lookbackDays 되돌아보며 메울 구간(일) · 연휴와 실패 회차를 함께 덮을 만큼
 * @param marketFilter 이 시장의 종목만 수집한다 · 비우면 전 시장 · 포털에는 {@code mrktCls} 로
 *     전달돼 호출 수도 함께 준다
 * @param topCount 시가총액({@code mrktTotAmt}) 상위 N 종목만 남긴다 · 0 이면 제한 없음
 */
@ConfigurationProperties(prefix = "app.market-data")
public record PublicDataProperties(
        String serviceKey,
        String baseUrl,
        int pageSize,
        int lookbackDays,
        Stock.Market marketFilter,
        int topCount) {

    private static final String DEFAULT_BASE_URL =
            "https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService";
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int DEFAULT_LOOKBACK_DAYS = 10;

    /**
     * 설정을 빠뜨렸을 때 0 이 그대로 흘러들면 페이지네이션이 끝나지 않는다. 값이 없으면
     * 기본값으로 되돌려 둔다.
     */
    public PublicDataProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (lookbackDays <= 0) {
            lookbackDays = DEFAULT_LOOKBACK_DAYS;
        }
        if (topCount < 0) {
            topCount = 0;
        }
    }

    /**
     * 인증키가 없으면 수집을 열지 않는다. 키 없이 돌리면 포털이 200 에 오류 문서를 실어
     * 보내 매 회차가 실패로 쌓이기만 한다 — 아예 건너뛰고 로그 한 줄을 남기는 편이 낫다.
     */
    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
