package ssafy.a507.backend.domain.market.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
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
import ssafy.a507.backend.domain.market.dto.IndexQuoteUpsert;
import ssafy.a507.backend.domain.market.entity.IndexQuote.IndexCode;

/**
 * 「금융위원회_지수시세정보」 응답을 지수 종가 행으로 옮기는 규칙.
 *
 * <p>포털은 하루에 168개 지수를 돌려준다(코스피 200, 코스닥 150, KRX 300 …). 그중 우리가
 * 쓰는 것은 "코스피"·"코스닥" 두 줄뿐이고, 이름으로 걸러 달라는 {@code idxNm} 파라미터는
 * 2026-09-03 직접 확인 결과 아무것도 돌려주지 않았다. 그래서 전부 받아 여기서 고른다.
 */
class PublicDataIndexClientTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 25);
    private static final LocalDate TO = LocalDate.of(2026, 9, 2);

    /** 포털이 주는 Decoding 키에는 이런 문자가 섞여 있다. 인코딩을 검증하려고 일부러 넣는다. */
    private static final String SERVICE_KEY = "ab+cd/ef==";

    /** 실제 응답에서 옮겨 온 모양이다 — 등락률이 ".23" 처럼 0 없이 오고, 섹터 지수가 섞여 있다. */
    private static final String ONE_PAGE =
            """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
            "body":{"numOfRows":4,"pageNo":1,"totalCount":4,"items":{"item":[
            {"basDt":"20260901","idxNm":"IT 서비스","idxCsf":"KOSPI시리즈","clpr":"1237.94","fltRt":"-.27"},
            {"basDt":"20260901","idxNm":"코스피","idxCsf":"KOSPI시리즈","clpr":"6,835.8","fltRt":".23"},
            {"basDt":"20260901","idxNm":"코스피 200","idxCsf":"KOSPI시리즈","clpr":"1075.3","fltRt":".32"},
            {"basDt":"20260901","idxNm":"코스닥","idxCsf":"KOSDAQ시리즈","clpr":"821.25","fltRt":"-1.56"}
            ]}}}}
            """;

    private static final MediaType JSON =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private MockRestServiceServer server;
    private PublicDataIndexClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PublicDataIndexClient(
                builder,
                new PublicDataProperties(SERVICE_KEY, "http://portal.test/stock", 4, 10, null, 0),
                new MarketIndexProperties("http://portal.test/index", 45, LocalDate.of(2020, 1, 2)));
    }

    @Test
    @DisplayName("코스피·코스닥 두 줄만 골라 행으로 옮긴다 — 섹터 지수와 파생 지수는 버린다")
    void 코스피_코스닥만_고른다() {
        server.expect(requestTo(containsString("/index/getStockMarketIndex")))
                .andRespond(withSuccess(utf8(ONE_PAGE), JSON));

        List<IndexQuoteUpsert> rows = client.fetchRange(FROM, TO);

        assertThat(rows)
                .extracting(IndexQuoteUpsert::indexCode)
                .containsExactly(IndexCode.KOSPI, IndexCode.KOSDAQ);
        assertThat(rows.get(0).tradeDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(rows.get(0).close()).as("천 단위 쉼표를 떼고 읽는다").isEqualByComparingTo("6835.8");
        assertThat(rows.get(1).close()).isEqualByComparingTo("821.25");

        server.verify();
    }

    @Test
    @DisplayName("구간은 beginBasDt·endBasDt 로, 인증키는 한 번만 인코딩해 보낸다")
    void 구간과_인증키를_실어_보낸다() {
        server.expect(requestTo(containsString("serviceKey=ab%2Bcd%2Fef%3D%3D")))
                .andExpect(queryParam("beginBasDt", "20260825"))
                .andExpect(queryParam("endBasDt", "20260902"))
                .andExpect(queryParam("resultType", "json"))
                .andRespond(withSuccess(utf8(ONE_PAGE), JSON));

        client.fetchRange(FROM, TO);

        server.verify();
    }

    @Test
    @DisplayName("데이터가 없는 구간은 빈 목록이다 — 오류가 아니다")
    void 빈_구간은_빈_목록이다() {
        String empty =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":4,"pageNo":1,"totalCount":0,"items":""}}}
                """;
        server.expect(requestTo(containsString("beginBasDt=20260825")))
                .andRespond(withSuccess(utf8(empty), JSON));

        assertThat(client.fetchRange(FROM, TO)).isEmpty();

        server.verify();
    }

    @Test
    @DisplayName("totalCount 가 남아 있으면 다음 페이지를 마저 부른다 — 구간이 넓으면 여러 페이지다")
    void 페이지를_끝까지_넘긴다() {
        String page1 =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":4,"pageNo":1,"totalCount":5,"items":{"item":[
                {"basDt":"20260902","idxNm":"코스피","clpr":"6850.1"},
                {"basDt":"20260902","idxNm":"코스닥","clpr":"830.5"},
                {"basDt":"20260902","idxNm":"코스피 200","clpr":"1080"},
                {"basDt":"20260901","idxNm":"코스피","clpr":"6835.8"}
                ]}}}}
                """;
        // 마지막 페이지에 한 건만 남으면 배열이 아니라 객체로 온다.
        String page2 =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":4,"pageNo":2,"totalCount":5,"items":{"item":
                {"basDt":"20260901","idxNm":"코스닥","clpr":"821.25"}
                }}}}
                """;
        server.expect(queryParam("pageNo", "1")).andRespond(withSuccess(utf8(page1), JSON));
        server.expect(queryParam("pageNo", "2")).andRespond(withSuccess(utf8(page2), JSON));

        List<IndexQuoteUpsert> rows = client.fetchRange(FROM, TO);

        assertThat(rows).hasSize(4);
        assertThat(rows.get(3).indexCode()).isEqualTo(IndexCode.KOSDAQ);
        assertThat(rows.get(3).tradeDate()).isEqualTo(LocalDate.of(2026, 9, 1));

        server.verify();
    }

    @Test
    @DisplayName("종가나 날짜가 없는 줄은 그 줄만 버린다")
    void 쓸_수_없는_행만_버린다() {
        String mixed =
                """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"numOfRows":4,"pageNo":1,"totalCount":3,"items":{"item":[
                {"basDt":"20260901","idxNm":"코스피","clpr":""},
                {"basDt":"","idxNm":"코스닥","clpr":"821.25"},
                {"basDt":"20260831","idxNm":"코스닥","clpr":"834.3"}
                ]}}}}
                """;
        server.expect(queryParam("pageNo", "1")).andRespond(withSuccess(utf8(mixed), JSON));

        List<IndexQuoteUpsert> rows = client.fetchRange(FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).tradeDate()).isEqualTo(LocalDate.of(2026, 8, 31));

        server.verify();
    }

    @Test
    @DisplayName("resultCode 가 00 이 아니면 예외다 — 그 회차는 수집하지 못한 것으로 둔다")
    void 포털_오류를_예외로_바꾼다() {
        String error =
                """
                {"response":{"header":{"resultCode":"22",
                "resultMsg":"LIMITED NUMBER OF SERVICE REQUESTS EXCEEDS ERROR"},"body":{}}}
                """;
        server.expect(queryParam("pageNo", "1")).andRespond(withSuccess(utf8(error), JSON));

        assertThatThrownBy(() -> client.fetchRange(FROM, TO))
                .isInstanceOf(PublicDataException.class)
                .hasMessageContaining("resultCode=22");
    }

    private static byte[] utf8(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
