package ssafy.a507.backend.domain.market.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 한국수출입은행 환율 API 응답을 원/달러 매매기준율 하나로 줄이는 규칙.
 *
 * <p>응답은 통화별 배열이고 오류도 배열 안의 {@code result} 로 온다(1 성공 · 2 DATA 코드 오류
 * · 3 인증키 오류 · 4 일 한도 초과). 주말·공휴일은 빈 배열이다. 값은 "1,385.1" 처럼 쉼표가
 * 섞인 문자열이다. 여기 적힌 모양은 2026-09-03 실제 응답에서 옮겼다.
 */
class KoreaeximFxClientTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 2);

    /**
     * TLS 신뢰 묶음을 붙이지 않는 가짜. 진짜는 요청 팩터리를 갈아 끼우므로 목 서버 바인딩이
     * 풀린다. 어느 이름의 묶음을 달라고 했는지만 기록해 둔다.
     */
    private String requestedBundle;

    private final RestClientSsl noSsl = new RestClientSsl() {
        @Override
        public Consumer<RestClient.Builder> fromBundle(String bundleName) {
            requestedBundle = bundleName;
            return builder -> {};
        }

        @Override
        public Consumer<RestClient.Builder> fromBundle(SslBundle bundle) {
            return builder -> {};
        }
    };

    private static final String BUSINESS_DAY =
            """
            [{"result":1,"cur_unit":"AED","ttb":"369.37","tts":"376.84","deal_bas_r":"373.11","cur_nm":"아랍에미리트 디르함"},
             {"result":1,"cur_unit":"JPY(100)","ttb":"901.32","tts":"919.53","deal_bas_r":"910.43","cur_nm":"일본 옌"},
             {"result":1,"cur_unit":"USD","ttb":"1,371.29","tts":"1,398.99","deal_bas_r":"1,385.14","cur_nm":"미국 달러"}]
            """;

    private static final MediaType JSON =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private MockRestServiceServer server;
    private KoreaeximFxClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KoreaeximFxClient(
                builder, noSsl, new KoreaeximProperties("http://fx.test/exchangeJSON", "test-key"));
    }

    @Test
    @DisplayName("수출입은행 전용 신뢰 묶음(koreaexim)을 붙인다 — 서버가 중간 인증서를 안 보내서다")
    void 전용_신뢰_묶음을_붙인다() {
        assertThat(requestedBundle).isEqualTo("koreaexim");
    }

    @Test
    @DisplayName("USD 줄의 매매기준율(deal_bas_r)을 읽는다 — 쉼표를 뗀다")
    void 달러_매매기준율을_읽는다() {
        server.expect(requestTo(startsWith("http://fx.test/exchangeJSON")))
                .andExpect(queryParam("authkey", "test-key"))
                .andExpect(queryParam("searchdate", "20260902"))
                .andExpect(queryParam("data", "AP01"))
                .andRespond(withSuccess(utf8(BUSINESS_DAY), JSON));

        Optional<BigDecimal> rate = client.fetchUsd(DATE);

        assertThat(rate).isPresent();
        assertThat(rate.get()).isEqualByComparingTo("1385.14");

        server.verify();
    }

    @Test
    @DisplayName("주말·공휴일은 빈 배열이다 — 빈 값이지 오류가 아니다")
    void 휴일은_빈_값이다() {
        server.expect(queryParam("searchdate", "20260902"))
                .andRespond(withSuccess(utf8("[]"), JSON));

        assertThat(client.fetchUsd(DATE)).isEmpty();

        server.verify();
    }

    @Test
    @DisplayName("일 한도 초과(result 4)는 따로 알아볼 수 있는 예외다 — 그 회차는 여기서 멈춰야 한다")
    void 일_한도_초과를_구분한다() {
        String exceeded =
                """
                [{"result":4,"cur_unit":null,"deal_bas_r":null,"cur_nm":null}]
                """;
        server.expect(queryParam("searchdate", "20260902"))
                .andRespond(withSuccess(utf8(exceeded), JSON));

        assertThatThrownBy(() -> client.fetchUsd(DATE))
                .isInstanceOf(KoreaeximFxClient.DailyLimitExceededException.class)
                .isInstanceOf(PublicDataException.class);
    }

    @Test
    @DisplayName("인증키 오류(result 3)는 원인이 메시지에 남는 예외다")
    void 인증키_오류를_예외로_바꾼다() {
        String unauthorized =
                """
                [{"result":3,"cur_unit":null,"deal_bas_r":null,"cur_nm":null}]
                """;
        server.expect(queryParam("searchdate", "20260902"))
                .andRespond(withSuccess(utf8(unauthorized), JSON));

        assertThatThrownBy(() -> client.fetchUsd(DATE))
                .isInstanceOf(PublicDataException.class)
                .isNotInstanceOf(KoreaeximFxClient.DailyLimitExceededException.class)
                .hasMessageContaining("result=3");
    }

    @Test
    @DisplayName("JSON 이 아닌 응답은 본문 앞부분을 실은 예외다")
    void 이상한_응답을_예외로_바꾼다() {
        server.expect(queryParam("searchdate", "20260902"))
                .andRespond(withSuccess(utf8("<html>점검 중</html>"), MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.fetchUsd(DATE))
                .isInstanceOf(PublicDataException.class)
                .hasMessageContaining("점검 중");
    }

    private static byte[] utf8(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
