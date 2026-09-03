package ssafy.a507.backend.domain.research.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.net.http.HttpClient;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * DART 전자공시 OpenAPI 호출 (ANT-RESEARCH-01).
 *
 * <p>WebClient 가 아니라 RestClient 인 이유 — 수집은 스케줄러 스레드에서 도는 블로킹 배치라
 * 논블로킹으로 얻는 것이 없고, 이 저장소의 외부 호출은 이미 RestClient 로 통일돼 있다
 * ({@code PublicDataStockClient} · {@code SsafyOAuthClient}).
 *
 * <p><b>DART 는 실패를 HTTP 상태로 알리지 않는다.</b> 키가 틀려도, 한도를 넘겨도, 데이터가
 * 없어도 전부 200 이고 본문의 {@code status} 로만 구분된다. 그래서 응답을 레코드로 바로
 * 바인딩하지 않고 트리로 읽어 봉투부터 확인한다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(DartProperties.class)
public class DartClient {

    /** 정상. */
    private static final String OK = "000";

    /** 조회된 데이터가 없음 — 오류가 아니라 빈 결과다. 신규 상장사나 공시 없는 구간에서 흔하다. */
    private static final String NO_DATA = "013";

    /** 요청 제한 초과. 이 회차는 접고 다음 날을 기다려야 하므로 메시지를 구분해 남긴다. */
    private static final String RATE_LIMITED = "020";

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 연결까지 기다리는 시간. 스케줄러 스레드가 하나뿐이라 무한정 기다리면 다른 배치가 함께 멈춘다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 응답을 다 받기까지 기다리는 시간. 고유번호 파일이 3MB 라 넉넉히 준다. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    /** 공시 목록 페이지 크기. DART 상한이 100 이다. */
    private static final int PAGE_SIZE = 100;

    /** 페이지 크기를 잘못 잡아도 무한히 돌지 않게 상한을 둔다. */
    private static final int MAX_PAGES = 20;

    /** {@code fnlttSinglAcnt} 의 사업보고서(연간) 코드. */
    public static final String ANNUAL_REPORT = "11011";

    /**
     * 응답을 읽기만 하는 용도라 앱의 직렬화 설정을 물려받을 이유가 없다. 우리 API 응답 규약이
     * 바뀐다고 DART 응답 해석이 함께 흔들리면 안 된다.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestClient restClient;
    private final DartProperties properties;

    /**
     * 일일 한도를 소진한 날. 한도는 키 단위라 갈래를 나눠도 함께 소진된다 — 갈래마다 break 로
     * 접어 봐야 같은 날 뒤따르는 회차가 다시 300 번을 헛되이 부른다. 하루 동안 문을 닫는다.
     */
    private volatile LocalDate quotaExhaustedOn;

    /**
     * <b>요청 팩터리를 JDK HttpClient 로 바꾼다.</b> 기본값인 Reactor Netty 로는 DART 에
     * 접속조차 못 한다 — {@code handshake_failure} 로 끊긴다.
     *
     * <p>2026-09-02 확인: {@code opendart.fss.or.kr} 은 <b>ECDHE 를 하나도 지원하지 않고</b>
     * 정적 RSA 와 DHE 만 받는다. Netty 는 기본 cipher 목록이 ECDHE 위주라 제안이 겹치지
     * 않는다. 반면 JDK 기본 목록에는 {@code TLS_DHE_RSA_*} 가 살아 있어 그대로 붙는다
     * (실제 협상 결과 {@code TLS_DHE_RSA_WITH_AES_128_CBC_SHA256}).
     *
     * <p>대안이던 "JVM 전역에서 TLS_RSA 를 다시 켠다"는 택하지 않았다. 앱 전체의 TLS 정책을
     * 낮추는 일이고, 서버 하나 때문에 다른 모든 연결의 안전성을 내릴 이유가 없다. 여기서
     * 바뀌는 것은 이 클라이언트의 HTTP 스택 하나뿐이다.
     *
     * <p>다른 외부 클라이언트({@code PublicDataStockClient} 등)는 손대지 않는다 — 그쪽 서버는
     * ECDHE 를 지원해 Netty 로 잘 붙는다.
     */
    @Autowired
    public DartClient(RestClient.Builder restClientBuilder, DartProperties properties) {
        this(restClientBuilder.requestFactory(timeoutAwareFactory()).build(), properties);
    }

    /**
     * <b>타임아웃을 반드시 건다.</b> JDK HttpClient 는 기본값이 "무한"이라, 반쯤 열린 소켓 하나가
     * 스케줄러 스레드를 영영 붙잡는다. 스레드 풀이 1개(Boot 기본)라 그 순간 일봉 수집을 포함한
     * 모든 배치가 재시작 전까지 멈춘다 — 예외도 로그도 남지 않아 가장 늦게 발견된다.
     */
    private static JdkClientHttpRequestFactory timeoutAwareFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /**
     * 테스트용. 이미 만들어진 클라이언트를 그대로 받는다 — 위 생성자가 요청 팩터리를 덮어쓰는
     * 탓에, 스텁 서버가 붙여 둔 팩터리까지 지워져 테스트가 실제 호출을 시도하게 된다.
     */
    DartClient(RestClient restClient, DartProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 고유번호 파일. 압축 3MB 가 풀리면 <b>29MB · 12만 건</b>이고 그중 상장사는 4천 건 안팎이다.
     *
     * <p>DOM 으로 올리지 않고 StAX 로 흘려 읽는다 — 12만 건짜리 트리를 통째로 힙에 세울 이유가
     * 없고, 우리가 남길 것은 종목코드가 붙은 행뿐이다. 비상장은 그 자리에서 버린다.
     */
    public List<CorpCodeRow> fetchListedCorpCodes() {
        byte[] archive = get("/corpCode.xml", builder -> {}, byte[].class);
        if (archive == null || archive.length == 0) {
            // 빈 200 이 온다. 아래 zip 생성에서 NPE 가 나면 그 예외는 DartException 이 아니라
            // 월간 회차(시드 + 기업개황)를 통째로 끌고 내려간다.
            throw new DartException("고유번호 파일이 비어 있다");
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            if (entry == null) {
                // 키가 틀리면 zip 이 아니라 JSON 오류 문서가 온다.
                throw new DartException("고유번호 파일이 zip 이 아니다: " + preview(archive));
            }
            return readCorpCodes(zip);
        } catch (IOException e) {
            throw new DartException("고유번호 파일을 풀지 못했다", e);
        }
    }

    /** 기업개황. 상장 폐지 등으로 없는 회사면 빈 값이 아니라 null 을 돌려준다. */
    public DartCompany fetchCompany(String corpCode) {
        JsonNode body = getJson("/company.json", builder -> builder.queryParam("corp_code", corpCode));
        if (body == null) {
            return null;
        }
        return new DartCompany(
                text(body, "corp_code"),
                text(body, "corp_name"),
                text(body, "corp_name_eng"),
                text(body, "stock_code"),
                text(body, "ceo_nm"),
                text(body, "induty_code"),
                text(body, "adres"),
                text(body, "hm_url"),
                text(body, "ir_url"),
                text(body, "est_dt"),
                text(body, "acc_mt"));
    }

    /**
     * 기간 안의 공시 목록. 마지막 페이지까지 따라간다.
     *
     * <p>겹쳐 받아도 상관없다 — 접수번호가 멱등 키라 이미 있는 건은 저장 단계에서 걸러진다.
     */
    public List<DartDisclosure> fetchDisclosures(String corpCode, LocalDate from, LocalDate to) {
        List<DartDisclosure> rows = new ArrayList<>();
        int page = 1;
        while (page <= MAX_PAGES) {
            final int current = page;
            JsonNode body = getJson(
                    "/list.json",
                    builder -> builder.queryParam("corp_code", corpCode)
                            .queryParam("bgn_de", from.format(YMD))
                            .queryParam("end_de", to.format(YMD))
                            .queryParam("page_no", current)
                            .queryParam("page_count", PAGE_SIZE));
            if (body == null) {
                break;
            }
            for (JsonNode node : body.path("list")) {
                rows.add(new DartDisclosure(
                        text(node, "corp_code"),
                        text(node, "stock_code"),
                        text(node, "rcept_no"),
                        text(node, "report_nm"),
                        text(node, "flr_nm"),
                        text(node, "rcept_dt")));
            }
            if (page >= body.path("total_page").asInt(1)) {
                break;
            }
            page++;
        }
        return rows;
    }

    /**
     * 주요 재무 계정. <b>한 번 호출로 3개 회계연도가 온다</b>(당기·전기·전전기)— 3년치를 위해
     * 세 번 부를 필요가 없다.
     *
     * <p>연결(CFS)과 별도(OFS)가 함께 오는데 연결을 우선한다. 종속회사가 없는 회사는 연결
     * 재무제표 자체가 없어 OFS 만 오므로, 한쪽만 고정하면 그런 회사가 통째로 빈다.
     */
    public List<DartFinancialSnapshot> fetchAnnualFinancials(String corpCode, int year) {
        JsonNode body = getJson(
                "/fnlttSinglAcnt.json",
                builder -> builder.queryParam("corp_code", corpCode)
                        .queryParam("bsns_year", String.valueOf(year))
                        .queryParam("reprt_code", ANNUAL_REPORT));
        if (body == null) {
            return List.of();
        }

        String preferred = preferredFsDiv(body.path("list"));
        // 연도별로 계정을 모은다. LinkedHashMap 이라 당기 → 전기 → 전전기 순서가 유지된다.
        Map<Integer, Accounts> byYear = new LinkedHashMap<>();
        String currency = null;
        String receiptNo = null;

        for (JsonNode row : body.path("list")) {
            if (!preferred.equals(text(row, "fs_div"))) {
                continue;
            }
            currency = text(row, "currency");
            receiptNo = text(row, "rcept_no");
            String account = text(row, "account_nm");
            // 당기 연도만 응답에 숫자로 있고 나머지는 거기서 세어 내려간다.
            int thisYear = parseInt(text(row, "bsns_year"), year);
            put(byYear, thisYear, account, text(row, "thstrm_amount"));
            put(byYear, thisYear - 1, account, text(row, "frmtrm_amount"));
            put(byYear, thisYear - 2, account, text(row, "bfefrmtrm_amount"));
        }

        List<DartFinancialSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<Integer, Accounts> entry : byYear.entrySet()) {
            DartFinancialSnapshot snapshot =
                    entry.getValue().toSnapshot(entry.getKey(), preferred, currency, receiptNo);
            if (!snapshot.isEmpty()) {
                snapshots.add(snapshot);
            }
        }
        return snapshots;
    }

    // ── 응답 봉투 ────────────────────────────────────────────

    /**
     * JSON 응답의 봉투를 확인하고 본문을 돌려준다. 데이터가 없으면 null 이다 — 오류가 아니라
     * 빈 결과이고, 호출부는 그 대상만 건너뛰면 된다.
     */
    private JsonNode getJson(String path, java.util.function.Consumer<UriComponentsBuilder> query) {
        String raw = get(path, query, String.class);
        if (raw == null || raw.isBlank()) {
            // 점검 페이지·게이트웨이 오작동으로 본문 없는 200 이 온다. readTree(null) 은
            // IllegalArgumentException 이라 아래 IOException 으로도, 호출부의 DartException
            // 으로도 잡히지 않아 남은 종목 전부를 건너뛰게 만든다.
            throw new DartException("DART 가 빈 응답을 보냈다: " + path);
        }
        JsonNode body;
        try {
            body = objectMapper.readTree(raw);
        } catch (IOException e) {
            throw new DartException("DART 응답을 JSON 으로 읽지 못했다: " + preview(raw), e);
        }

        String status = text(body, "status");
        if (OK.equals(status)) {
            return body;
        }
        if (NO_DATA.equals(status)) {
            return null;
        }
        if (RATE_LIMITED.equals(status)) {
            quotaExhaustedOn = LocalDate.now(KST);
            throw DartException.rateLimited();
        }
        throw new DartException("DART 오류 status=" + status + " message=" + text(body, "message"));
    }

    private <T> T get(
            String path, java.util.function.Consumer<UriComponentsBuilder> query, Class<T> type) {
        if (!properties.isConfigured()) {
            throw new DartException("DART_API_KEY 가 비어 있다");
        }
        if (LocalDate.now(KST).equals(quotaExhaustedOn)) {
            // 오늘은 이미 한도를 넘겼다. 갈래가 달라도 키가 같아 결과는 같다.
            throw DartException.rateLimited();
        }
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromUriString(properties.baseUrl() + path)
                        .queryParam("crtfc_key", properties.apiKey());
        query.accept(builder);
        try {
            return restClient.get().uri(builder.build().encode().toUri()).retrieve().body(type);
        } catch (RestClientException e) {
            throw new DartException("DART 호출 실패: " + path, e);
        }
    }

    // ── 고유번호 파일 파싱 ───────────────────────────────────

    /** 종목코드가 붙은 행만 남긴다. 비상장 11만 건은 우리 화면에 쓸 데가 없다. */
    private List<CorpCodeRow> readCorpCodes(InputStream xml) {
        List<CorpCodeRow> rows = new ArrayList<>();
        XMLStreamReader reader = null;
        try {
            reader = newXmlInputFactory().createXMLStreamReader(xml);
            // 한 엘리먼트의 텍스트가 여러 CHARACTERS 이벤트로 쪼개져 온다. 특히 엔티티 참조
            // 경계에서 갈라지는데, 상장사 이름에 & 가 들어간 회사가 실제로 20곳 있다
            // (세림B&G · HL D&I · 동국S&C …). 덮어쓰면 그 회사 이름이 잘린다.
            StringBuilder text = new StringBuilder();
            String corpCode = null;
            String corpName = null;
            String stockCode = null;

            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT -> text.setLength(0);
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                            text.append(reader.getText());
                    case XMLStreamConstants.END_ELEMENT -> {
                        String value = text.toString().trim();
                        switch (reader.getLocalName()) {
                            case "corp_code" -> corpCode = value;
                            case "corp_name" -> corpName = value;
                            case "stock_code" -> stockCode = value;
                            case "list" -> {
                                // 비상장은 stock_code 가 빈 문자열이 아니라 공백 한 칸이다.
                                if (stockCode != null && !stockCode.isBlank()) {
                                    rows.add(new CorpCodeRow(corpCode, corpName, stockCode));
                                }
                                corpCode = null;
                                corpName = null;
                                stockCode = null;
                            }
                            default -> {}
                        }
                        text.setLength(0);
                    }
                    default -> {}
                }
            }
        } catch (XMLStreamException e) {
            throw new DartException("고유번호 XML 파싱 실패", e);
        } finally {
            closeQuietly(reader);
        }
        return rows;
    }

    /**
     * 외부 엔티티를 끈다. 우리가 부른 주소에서 온 파일이지만, XML 파서를 열 때 이걸 켠 채로
     * 두면 파일 내용이 서버의 로컬 파일을 읽어가는 통로가 된다(XXE).
     */
    private XMLInputFactory newXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        return factory;
    }

    private void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException e) {
            log.debug("XML 리더를 닫지 못했다", e);
        }
    }

    // ── 값 변환 ──────────────────────────────────────────────

    private static void put(Map<Integer, Accounts> byYear, int year, String account, String amount) {
        BigInteger value = parseAmount(amount);
        if (value != null) {
            byYear.computeIfAbsent(year, key -> new Accounts()).put(account, value);
        }
    }

    /**
     * 어느 재무제표를 쓸지 고른다. 연결(CFS)이 있으면 연결이다 — 지배기업 실적만 담은 별도보다
     * 그룹 전체를 담은 연결이 투자 판단의 기본이다.
     */
    private static String preferredFsDiv(JsonNode list) {
        for (JsonNode row : list) {
            if ("CFS".equals(text(row, "fs_div"))) {
                return "CFS";
            }
        }
        return "OFS";
    }

    /** {@code "247,684,612,000,000"} · 음수는 {@code "-1,234"}. 빈 값·하이픈은 null 이다. */
    private static BigInteger parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace(",", "").replace(" ", "").trim();
        if (cleaned.isEmpty() || "-".equals(cleaned)) {
            return null;
        }
        try {
            return new BigInteger(cleaned);
        } catch (NumberFormatException e) {
            log.debug("금액을 읽지 못했다: {}", raw);
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText().trim();
    }

    private static String preview(String raw) {
        if (raw == null) {
            return "(빈 응답)";
        }
        return raw.length() <= 200 ? raw : raw.substring(0, 200);
    }

    private static String preview(byte[] raw) {
        return raw == null ? "(빈 응답)" : preview(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 연도 하나의 계정 모음. 계정명이 DART 고정 어휘라 문자열로 받아 자리를 찾아 넣는다. */
    private static final class Accounts {

        private final Map<String, BigInteger> values = new LinkedHashMap<>();

        void put(String account, BigInteger value) {
            values.putIfAbsent(account, value);
        }

        DartFinancialSnapshot toSnapshot(int year, String fsDiv, String currency, String receiptNo) {
            return new DartFinancialSnapshot(
                    year,
                    fsDiv,
                    currency,
                    receiptNo,
                    first("매출액"),
                    // 금융지주는 "영업이익" 대신 "영업이익(손실)" 로 낸다(KB금융 실측).
                    first("영업이익", "영업이익(손실)"),
                    first("당기순이익(손실)", "당기순이익"),
                    first("자산총계"),
                    first("부채총계"),
                    first("자본총계"));
        }

        /** 계정명은 제출인이 쓴 라벨이 그대로 온다. 같은 뜻의 표기를 순서대로 찾는다. */
        private BigInteger first(String... accounts) {
            for (String account : accounts) {
                BigInteger value = values.get(account);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
    }
}
