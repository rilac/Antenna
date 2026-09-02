package ssafy.a507.backend.domain.research.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * DART 응답의 실제 모양을 고정해 둔다. 여기 적힌 것들은 전부 2026-09-02 에 실제 호출로 확인한
 * 형태다 — 계정 행 하나가 3개 회계연도를 담고 있는 것, 연결·별도가 함께 오는 것, 금액에 천
 * 단위 쉼표가 붙는 것, 실패가 HTTP 상태가 아니라 본문 {@code status} 로만 오는 것.
 *
 * <p>특히 <b>재무 응답을 연도별로 펴는 계산</b>과 <b>고유번호 XML 스트리밍</b>은 눈으로
 * 검증할 수 없는 자리라 여기서 막지 못하면 화면에서야 드러난다.
 */
class DartClientTest {

    private static final String API_KEY = "test-key";

    private static final MediaType JSON =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private static final String COMPANY =
            """
            {"status":"000","message":"정상","corp_code":"00126380","corp_name":"삼성전자(주)",
             "corp_name_eng":"SAMSUNG ELECTRONICS CO,.LTD","stock_name":"삼성전자",
             "stock_code":"005930","ceo_nm":"전영현, 노태문","corp_cls":"Y",
             "adres":"경기도 수원시 영통구  삼성로 129 (매탄동)","hm_url":"www.samsung.com/sec",
             "ir_url":"","induty_code":"264","est_dt":"19690113","acc_mt":"12"}
            """;

    /** 계정 하나가 당기·전기·전전기를 함께 담는다. 연결(CFS)과 별도(OFS)가 같이 온다. */
    private static final String FINANCIALS =
            """
            {"status":"000","message":"정상","list":[
             {"rcept_no":"20260310002820","bsns_year":"2025","fs_div":"CFS","fs_nm":"연결재무제표",
              "sj_div":"IS","account_nm":"매출액","currency":"KRW",
              "thstrm_amount":"300,870,903,000,000","frmtrm_amount":"258,935,494,000,000",
              "bfefrmtrm_amount":"232,723,000,000,000"},
             {"rcept_no":"20260310002820","bsns_year":"2025","fs_div":"CFS","fs_nm":"연결재무제표",
              "sj_div":"IS","account_nm":"영업이익","currency":"KRW",
              "thstrm_amount":"32,725,961,000,000","frmtrm_amount":"6,566,976,000,000",
              "bfefrmtrm_amount":"-11,526,000,000,000"},
             {"rcept_no":"20260310002820","bsns_year":"2025","fs_div":"CFS","fs_nm":"연결재무제표",
              "sj_div":"BS","account_nm":"자산총계","currency":"KRW",
              "thstrm_amount":"566,942,110,000,000","frmtrm_amount":"514,531,948,000,000",
              "bfefrmtrm_amount":"-"},
             {"rcept_no":"20260310002820","bsns_year":"2025","fs_div":"OFS","fs_nm":"재무제표",
              "sj_div":"IS","account_nm":"매출액","currency":"KRW",
              "thstrm_amount":"1,000","frmtrm_amount":"2,000","bfefrmtrm_amount":"3,000"}
            ]}
            """;

    private static final String DISCLOSURES_PAGE_1 =
            """
            {"status":"000","message":"정상","page_no":1,"total_page":2,"total_count":3,"list":[
             {"corp_code":"00126380","corp_name":"삼성전자","stock_code":"005930",
              "report_nm":"임원ㆍ주요주주특정증권등소유상황보고서","rcept_no":"20260831000066",
              "flr_nm":"김은용","rcept_dt":"20260831","rm":""}
            ]}
            """;

    private static final String DISCLOSURES_PAGE_2 =
            """
            {"status":"000","message":"정상","page_no":2,"total_page":2,"total_count":3,"list":[
             {"corp_code":"00126380","corp_name":"삼성전자","stock_code":"005930",
              "report_nm":"주식등의대량보유상황보고서(일반)","rcept_no":"20260828001916",
              "flr_nm":"삼성물산","rcept_dt":"20260828","rm":""}
            ]}
            """;

    private static final String NO_DATA = "{\"status\":\"013\",\"message\":\"조회된 데이타가 없습니다.\"}";
    private static final String RATE_LIMIT = "{\"status\":\"020\",\"message\":\"요청 제한을 초과하였습니다.\"}";
    private static final String BAD_KEY = "{\"status\":\"010\",\"message\":\"등록되지 않은 키입니다.\"}";

    private MockRestServiceServer server;
    private DartClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // 스텁 서버가 붙인 요청 팩터리를 살려야 하므로 이미 만들어진 RestClient 를 넘긴다.
        client = new DartClient(builder.build(), new DartProperties(API_KEY, "http://dart.test/api", 7, 0));
    }

    @Test
    @DisplayName("기업개황을 프로필 값으로 옮긴다")
    void 기업개황() {
        server.expect(requestTo(containsString("corp_code=00126380")))
                .andRespond(withSuccess(COMPANY, JSON));

        DartCompany company = client.fetchCompany("00126380");

        assertThat(company.stockCode()).isEqualTo("005930");
        assertThat(company.corpName()).isEqualTo("삼성전자(주)");
        assertThat(company.ceoName()).isEqualTo("전영현, 노태문");
        assertThat(company.establishedDate()).isEqualTo("19690113");
        assertThat(company.accountMonth()).isEqualTo("12");
        server.verify();
    }

    @Test
    @DisplayName("재무 응답 한 번이 3개 회계연도로 펴진다 — 연결(CFS)을 쓴다")
    void 재무_3개년() {
        server.expect(requestTo(containsString("bsns_year=2025")))
                .andRespond(withSuccess(FINANCIALS, JSON));

        List<DartFinancialSnapshot> snapshots = client.fetchAnnualFinancials("00126380", 2025);

        assertThat(snapshots).extracting(DartFinancialSnapshot::year)
                .containsExactlyInAnyOrder(2025, 2024, 2023);

        DartFinancialSnapshot latest =
                snapshots.stream().filter(s -> s.year() == 2025).findFirst().orElseThrow();
        assertThat(latest.fsDiv()).as("연결이 있으면 별도가 아니라 연결이다").isEqualTo("CFS");
        assertThat(latest.revenue()).isEqualTo(new BigInteger("300870903000000"));
        assertThat(latest.totalAssets()).isEqualTo(new BigInteger("566942110000000"));
        assertThat(latest.currency()).isEqualTo("KRW");
        assertThat(latest.receiptNo()).isEqualTo("20260310002820");

        DartFinancialSnapshot oldest =
                snapshots.stream().filter(s -> s.year() == 2023).findFirst().orElseThrow();
        assertThat(oldest.operatingProfit()).as("음수도 읽는다").isEqualTo(new BigInteger("-11526000000000"));
        assertThat(oldest.totalAssets()).as("하이픈은 null 이다 — 0 이 아니다").isNull();

        // 별도(OFS)의 1,000 이 섞였다면 매출이 그 값으로 덮였을 것이다.
        assertThat(latest.revenue()).isNotEqualTo(new BigInteger("1000"));
        server.verify();
    }

    @Test
    @DisplayName("공시 목록은 마지막 페이지까지 따라가고 원문 URL 을 접수번호로 조립한다")
    void 공시_페이징() {
        server.expect(requestTo(containsString("page_no=1")))
                .andRespond(withSuccess(DISCLOSURES_PAGE_1, JSON));
        server.expect(requestTo(containsString("page_no=2")))
                .andRespond(withSuccess(DISCLOSURES_PAGE_2, JSON));

        List<DartDisclosure> rows = client.fetchDisclosures(
                "00126380", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 2));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).receiptNo()).isEqualTo("20260831000066");
        assertThat(rows.get(0).originUrl())
                .isEqualTo("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260831000066");
        assertThat(rows.get(1).filerName()).isEqualTo("삼성물산");
        server.verify();
    }

    @Test
    @DisplayName("데이터 없음(013)은 오류가 아니라 빈 결과다")
    void 데이터_없음() {
        server.expect(requestTo(containsString("company.json")))
                .andRespond(withSuccess(NO_DATA, JSON));

        assertThat(client.fetchCompany("00000000")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("한도 초과(020)는 다른 실패와 구분된다 — 회차를 접어야 한다")
    void 한도_초과() {
        server.expect(requestTo(containsString("company.json")))
                .andRespond(withSuccess(RATE_LIMIT, JSON));

        assertThatThrownBy(() -> client.fetchCompany("00126380"))
                .isInstanceOfSatisfying(DartException.class, e -> assertThat(e.isRateLimited()).isTrue());
        server.verify();
    }

    @Test
    @DisplayName("HTTP 는 200 인데 키가 틀린 응답도 실패로 잡는다")
    void 잘못된_키() {
        server.expect(requestTo(containsString("company.json")))
                .andRespond(withSuccess(BAD_KEY, JSON));

        assertThatThrownBy(() -> client.fetchCompany("00126380"))
                .isInstanceOf(DartException.class)
                .hasMessageContaining("010");
        server.verify();
    }

    @Test
    @DisplayName("고유번호 파일에서 상장사만 남긴다 — 비상장은 그 자리에서 버린다")
    void 고유번호_시드() {
        server.expect(requestTo(containsString("corpCode.xml")))
                .andRespond(withSuccess(corpCodeZip(), MediaType.APPLICATION_OCTET_STREAM));

        List<CorpCodeRow> rows = client.fetchListedCorpCodes();

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(CorpCodeRow::stockCode).containsExactly("005930", "035720");
        assertThat(rows.get(0).corpCode()).isEqualTo("00126380");
        assertThat(rows.get(0).corpName()).isEqualTo("삼성전자");
        // 이름에 & 가 든 상장사가 실제로 20곳 있다. StAX 는 엔티티 경계에서 CHARACTERS 를
        // 쪼개 보내므로, 이어 붙이지 않으면 "동국S" 로 잘린다.
        assertThat(rows.get(1).corpName()).isEqualTo("동국S&C");
        server.verify();
    }

    @Test
    @DisplayName("키가 틀리면 zip 대신 오류 문서가 온다 — 그것도 실패로 잡는다")
    void 시드가_zip이_아니면_실패() {
        server.expect(requestTo(containsString("corpCode.xml")))
                .andRespond(withSuccess(BAD_KEY.getBytes(StandardCharsets.UTF_8), JSON));

        assertThatThrownBy(() -> client.fetchListedCorpCodes())
                .isInstanceOf(DartException.class)
                .hasMessageContaining("zip");
        server.verify();
    }

    /** 실제 파일과 같은 구조 — zip 안에 CORPCODE.xml 하나. 비상장은 stock_code 가 빈 문자열이다. */
    private static byte[] corpCodeZip() {
        String xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                  <list><corp_code>00126380</corp_code><corp_name>삼성전자</corp_name>
                    <corp_eng_name>SAMSUNG</corp_eng_name><stock_code>005930</stock_code>
                    <modify_date>20260101</modify_date></list>
                  <list><corp_code>00999999</corp_code><corp_name>비상장회사</corp_name>
                    <corp_eng_name>Private</corp_eng_name><stock_code> </stock_code>
                    <modify_date>20260101</modify_date></list>
                  <list><corp_code>00258801</corp_code><corp_name>동국S&amp;C</corp_name>
                    <corp_eng_name>Dongkuk S&amp;C</corp_eng_name><stock_code>035720</stock_code>
                    <modify_date>20260101</modify_date></list>
                </result>
                """;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
