package ssafy.a507.backend.domain.market.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * 포털 응답의 변덕을 고정해 둔다. 여기 적힌 모양들은 전부 실제로 오는 것들이다 — 데이터가
 * 없는 날의 빈 items, 천 단위 쉼표가 섞인 숫자, 값 없는 칸의 하이픈, 인증키가 틀렸을 때
 * 200 에 실려 오는 XML. 하나라도 가정하고 들어가면 그날 수집이 통째로 날아간다.
 */
class PublicDataStockClientTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 28);

    /** 포털이 주는 Decoding 키에는 이런 문자가 섞여 있다. 인코딩을 검증하려고 일부러 넣는다. */
    private static final String SERVICE_KEY = "ab+cd/ef==";

    private static final String ONE_PAGE =
            """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
            "body":{"numOfRows":2,"pageNo":1,"totalCount":2,"items":{"item":[
            {"basDt":"20260828","srtnCd":"005930","isinCd":"KR7005930003","itmsNm":"삼성전자",
             "mrktCtg":"KOSPI","clpr":"71500","mkp":"71000","hipr":"71800","lopr":"70900",
             "trqu":"12345678","lstgStCnt":"5969782550","mrktTotAmt":"426839452325000"},
            {"basDt":"20260828","srtnCd":"035720","itmsNm":"카카오","mrktCtg":"KOSDAQ",
             "clpr":"41,250","mkp":"-","hipr":"41500","lopr":"41000","trqu":"987654"}
            ]}}}}
            """;

    /** 포털이 실제로 붙여 보내는 형태. 클라이언트가 본문을 UTF-8 로 해독하는지까지 함께 본다. */
    private static final MediaType JSON =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private static final MediaType XML =
            new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8);

    private MockRestServiceServer server;
    private PublicDataStockClient client;

    @BeforeEach
    void setUp() {
        client = clientWith(null, 0);
    }

    /** 수집 범위 설정(market-filter · top-count)만 바꿔 클라이언트를 다시 만든다. */
    private PublicDataStockClient clientWith(Stock.Market marketFilter, int topCount) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new PublicDataStockClient(
                builder,
                new PublicDataProperties(
                        SERVICE_KEY, "http://portal.test/svc", 2, 10, marketFilter, topCount));
    }

    @Test
    @DisplayName("한 페이지 응답을 행으로 옮긴다 — 쉼표와 하이픈도 처리한다")
    void 응답을_행으로_옮긴다() {
        server.expect(requestTo(containsString("basDt=20260828")))
                .andRespond(withSuccess(utf8(ONE_PAGE), JSON));

        List<StockPriceRow> rows = client.fetchDay(BASE_DATE);

        assertThat(rows).hasSize(2);

        StockPriceRow samsung = rows.get(0);
        assertThat(samsung.stockCode()).isEqualTo("005930");
        assertThat(samsung.stockName()).isEqualTo("삼성전자");
        assertThat(samsung.market()).isEqualTo(Stock.Market.KOSPI);
        assertThat(samsung.tradeDate()).isEqualTo(BASE_DATE);
        assertThat(samsung.close()).isEqualByComparingTo("71500");
        assertThat(samsung.volume()).isEqualTo(12_345_678L);
        assertThat(samsung.listedShares()).as("PER·PBR 의 분모 재료").isEqualTo(5_969_782_550L);

        StockPriceRow kakao = rows.get(1);
        assertThat(kakao.close()).as("천 단위 쉼표를 떼고 읽는다").isEqualByComparingTo("41250");
        assertThat(kakao.open()).as("값 없는 칸의 하이픈은 null 이다").isNull();
        assertThat(kakao.market()).isEqualTo(Stock.Market.KOSDAQ);
        assertThat(kakao.listedShares()).as("칸이 없으면 null — 0 주로 읽히면 안 된다").isNull();

        server.verify();
    }

    @Test
    @DisplayName("인증키는 한 번만 인코딩해 보낸다")
    void 인증키를_인코딩해_보낸다() {
        // 빌더에 맡기면 '+' 가 그대로 나가고 서버는 그것을 공백으로 읽는다.
        server.expect(requestTo(containsString("serviceKey=ab%2Bcd%2Fef%3D%3D")))
                .andRespond(withSuccess(utf8(ONE_PAGE), JSON));

        client.fetchDay(BASE_DATE);

        server.verify();
    }

    @Test
    @DisplayName("데이터가 없는 날은 빈 목록이다 — 오류가 아니다")
    void 공휴일은_빈_목록이다() {
        // 장이 서지 않은 날의 items 는 객체가 아니라 빈 문자열로 온다.
        String empty =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":2,"pageNo":1,"totalCount":0,"items":""}}}
                """;
        server.expect(requestTo(containsString("basDt=20260828")))
                .andRespond(withSuccess(utf8(empty), JSON));

        assertThat(client.fetchDay(BASE_DATE)).isEmpty();

        server.verify();
    }

    @Test
    @DisplayName("totalCount 가 남아 있으면 다음 페이지를 마저 부른다")
    void 페이지를_끝까지_넘긴다() {
        String page1 =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":2,"pageNo":1,"totalCount":3,"items":{"item":[
                {"basDt":"20260828","srtnCd":"005930","itmsNm":"삼성전자","mrktCtg":"KOSPI","clpr":"71500"},
                {"basDt":"20260828","srtnCd":"035720","itmsNm":"카카오","mrktCtg":"KOSDAQ","clpr":"41250"}
                ]}}}}
                """;
        // 마지막 페이지에 한 건만 남으면 배열이 아니라 객체로 온다.
        String page2 =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":2,"pageNo":2,"totalCount":3,"items":{"item":
                {"basDt":"20260828","srtnCd":"000660","itmsNm":"SK하이닉스","mrktCtg":"KOSPI","clpr":"180000"}
                }}}}
                """;
        server.expect(requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(utf8(page1), JSON));
        server.expect(requestTo(containsString("pageNo=2")))
                .andRespond(withSuccess(utf8(page2), JSON));

        List<StockPriceRow> rows = client.fetchDay(BASE_DATE);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(2).stockCode()).isEqualTo("000660");

        server.verify();
    }

    @Test
    @DisplayName("resultCode 가 00 이 아니면 그날은 수집하지 못한 것으로 둔다")
    void 포털_오류를_예외로_바꾼다() {
        String error =
                """
                {"response":{"header":{"resultCode":"22",
                "resultMsg":"LIMITED NUMBER OF SERVICE REQUESTS EXCEEDS ERROR"},"body":{}}}
                """;
        server.expect(requestTo(containsString("basDt=20260828")))
                .andRespond(withSuccess(utf8(error), JSON));

        assertThatThrownBy(() -> client.fetchDay(BASE_DATE))
                .isInstanceOf(PublicDataException.class)
                .hasMessageContaining("resultCode=22")
                .hasMessageContaining("LIMITED NUMBER");
    }

    @Test
    @DisplayName("인증키가 틀려 XML 이 와도 원인이 메시지에 남는다")
    void 인증키_오류를_알아볼_수_있게_한다() {
        // resultType=json 을 보내도 인증 단계에서 걸리면 200 에 XML 이 실려 온다.
        String xml =
                """
                <OpenAPI_ServiceResponse><cmmMsgHeader>
                <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                <returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
                """;
        server.expect(requestTo(containsString("basDt=20260828")))
                .andRespond(withSuccess(utf8(xml), XML));

        assertThatThrownBy(() -> client.fetchDay(BASE_DATE))
                .isInstanceOf(PublicDataException.class)
                .hasMessageContaining("인증키")
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("종목코드가 이상하거나 종가가 없는 행은 그 줄만 버린다")
    void 쓸_수_없는_행만_버린다() {
        String mixed =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":2,"pageNo":1,"totalCount":3,"items":{"item":[
                {"basDt":"20260828","srtnCd":"A005930","itmsNm":"코드이상","mrktCtg":"KOSPI","clpr":"71500"},
                {"basDt":"20260828","srtnCd":"035720","itmsNm":"종가없음","mrktCtg":"KOSDAQ","clpr":""},
                {"basDt":"20260828","srtnCd":"000660","itmsNm":"SK하이닉스","mrktCtg":"KOSPI","clpr":"180000"}
                ]}}}}
                """;
        server.expect(requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(utf8(mixed), JSON));
        // totalCount 3 > pageSize 2 라 다음 페이지도 부른다.
        server.expect(requestTo(containsString("pageNo=2")))
                .andRespond(
                        withSuccess(
                                utf8(
                                        """
                                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                                        "body":{"numOfRows":2,"pageNo":2,"totalCount":3,"items":""}}}
                                        """),
                                JSON));

        List<StockPriceRow> rows = client.fetchDay(BASE_DATE);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).stockCode()).isEqualTo("000660");

        server.verify();
    }

    // ── 수집 범위 축소 — KOSPI 시가총액 상위 N (팀 결정: KOSPI 300) ──────────

    @Test
    @DisplayName("시장 필터는 mrktCls 로 나가고, 응답에 섞여 온 다른 시장 행은 떨어진다")
    void 시장_필터를_적용한다() {
        client = clientWith(Stock.Market.KOSPI, 0);
        // ONE_PAGE 에는 KOSPI(삼성전자)와 KOSDAQ(카카오)이 섞여 있다 — 포털이 mrktCls 를
        // 무시하고 전 시장을 돌려줘도 결과가 KOSPI 만이어야 한다.
        server.expect(requestTo(containsString("mrktCls=KOSPI")))
                .andRespond(withSuccess(utf8(ONE_PAGE), JSON));

        List<StockPriceRow> rows = client.fetchDay(BASE_DATE);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).stockCode()).isEqualTo("005930");

        server.verify();
    }

    @Test
    @DisplayName("시가총액 상위 N 종목만 남긴다 — 전 페이지를 받은 뒤에 고른다")
    void 시가총액_상위만_남긴다() {
        client = clientWith(Stock.Market.KOSPI, 2);
        String page1 =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":2,"pageNo":1,"totalCount":3,"items":{"item":[
                {"basDt":"20260828","srtnCd":"900001","itmsNm":"소형주","mrktCtg":"KOSPI",
                 "clpr":"1000","mrktTotAmt":"1000000000000"},
                {"basDt":"20260828","srtnCd":"000660","itmsNm":"SK하이닉스","mrktCtg":"KOSPI",
                 "clpr":"180000","mrktTotAmt":"100000000000000"}
                ]}}}}
                """;
        String page2 =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":2,"pageNo":2,"totalCount":3,"items":{"item":
                {"basDt":"20260828","srtnCd":"005930","itmsNm":"삼성전자","mrktCtg":"KOSPI",
                 "clpr":"71500","mrktTotAmt":"400000000000000"}
                }}}}
                """;
        server.expect(requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(utf8(page1), JSON));
        server.expect(requestTo(containsString("pageNo=2")))
                .andRespond(withSuccess(utf8(page2), JSON));

        List<StockPriceRow> rows = client.fetchDay(BASE_DATE);

        // 마지막 페이지에 온 삼성전자가 1등이다 — 페이지 순서가 아니라 시가총액이 기준이다.
        assertThat(rows).extracting(StockPriceRow::stockCode).containsExactly("005930", "000660");

        server.verify();
    }

    /** 클라이언트가 바이트를 받아 UTF-8 로 해독하므로, 목 응답도 같은 바이트로 고정한다. */
    private static byte[] utf8(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
