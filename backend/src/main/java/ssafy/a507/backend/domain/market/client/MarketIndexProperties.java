package ssafy.a507.backend.domain.market.client;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공공데이터포털 「금융위원회_지수시세정보」 수집 설정(ANT-DATA-04).
 *
 * <p>인증키는 주식시세와 같은 것을 쓴다({@link PublicDataProperties#serviceKey()}) — 포털이
 * 계정 하나에 키 하나를 주고 API 마다 활용신청만 따로 받는다. 그래서 여기에는 키가 없다.
 *
 * @param baseUrl 서비스 기본 주소 · 주식시세와 서비스 경로가 다르다
 * @param lookbackDays 매 회차 되돌아보며 다시 받는 구간(일) · 홈 미니차트가 30 영업일을 쓰므로
 *     달력으로 45일은 덮어야 첫 회차부터 그림이 그려진다
 * @param from 포털이 지수 데이터를 주는 첫 날 · DB 가 비어 있을 때의 수집 시작점이자 환율의
 *     거래일 달력을 만드는 시작점
 */
@ConfigurationProperties(prefix = "app.market-data.index")
public record MarketIndexProperties(String baseUrl, int lookbackDays, LocalDate from) {

    private static final String DEFAULT_BASE_URL =
            "https://apis.data.go.kr/1160100/service/GetMarketIndexInfoService";
    private static final int DEFAULT_LOOKBACK_DAYS = 45;
    private static final LocalDate DEFAULT_FROM = LocalDate.of(2020, 1, 2);

    public MarketIndexProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (lookbackDays <= 0) {
            lookbackDays = DEFAULT_LOOKBACK_DAYS;
        }
        if (from == null) {
            from = DEFAULT_FROM;
        }
    }
}
