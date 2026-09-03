package ssafy.a507.backend.domain.market.client;

import static ssafy.a507.backend.domain.market.client.PublicDataJson.decimal;
import static ssafy.a507.backend.domain.market.client.PublicDataJson.items;
import static ssafy.a507.backend.domain.market.client.PublicDataJson.text;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ssafy.a507.backend.domain.market.dto.IndexQuoteUpsert;
import ssafy.a507.backend.domain.market.entity.IndexQuote.IndexCode;

/**
 * 「금융위원회_지수시세정보」에서 코스피·코스닥 종가를 구간째로 받아 온다.
 *
 * <p><b>날짜별이 아니라 구간별이다.</b> 이 API 는 {@code beginBasDt}·{@code endBasDt} 를
 * 받아 여러 날을 한 번에 준다. 주식시세처럼 날짜마다 부를 이유가 없고, 2020년부터의 백필도
 * 페이지 300장쯤(하루 168행 × 1,660일 ÷ 1,000)이면 끝난다.
 *
 * <p><b>이름으로 걸러 달라고 하지 않는다.</b> {@code idxNm=코스피} 를 보내면 포털이 빈 응답을
 * 준다(2026-09-03 확인 — 한글 파라미터를 못 읽는 것으로 보인다). 그래서 그날의 168개 지수를
 * 전부 받아 여기서 두 줄만 고른다. 행 수가 84배 많지만 페이지 몇 장 차이라 감수한다.
 */
@Slf4j
@Component
@EnableConfigurationProperties({PublicDataProperties.class, MarketIndexProperties.class})
public class PublicDataIndexClient {

    private static final String PATH = "/getStockMarketIndex";
    private static final DateTimeFormatter BAS_DT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 포털 응답의 지수명 → 우리 코드. 여기 없는 지수(코스피 200 · KRX 300 …)는 버린다. */
    private static final Map<String, IndexCode> NAMES =
            Map.of("코스피", IndexCode.KOSPI, "코스닥", IndexCode.KOSDAQ);

    /**
     * 페이지 상한. page-size 1,000 이면 50만 행 ≈ 12년치라 2020년부터의 백필도 안에 든다.
     * 페이지 크기를 잘못 잡아도 무한히 돌지 않게 두는 안전장치다.
     */
    private static final int MAX_PAGES = 500;

    private final RestClient restClient;
    private final PublicDataProperties portal;
    private final MarketIndexProperties properties;

    public PublicDataIndexClient(
            RestClient.Builder restClientBuilder,
            PublicDataProperties portal,
            MarketIndexProperties properties) {
        this.restClient = restClientBuilder.build();
        this.portal = portal;
        this.properties = properties;
    }

    /**
     * 구간의 코스피·코스닥 종가를 받는다. 구간에 영업일이 없으면 빈 목록이다 — 오류가 아니다.
     *
     * @throws PublicDataException 호출·응답이 실패했을 때. 이번 회차는 적재하지 않는다.
     */
    public List<IndexQuoteUpsert> fetchRange(LocalDate from, LocalDate to) {
        List<IndexQuoteUpsert> rows = new ArrayList<>();
        int dropped = 0;
        int pageNo = 1;

        while (true) {
            JsonNode body = requestPage(from, to, pageNo);

            for (JsonNode item : items(body)) {
                IndexCode code = NAMES.get(text(item, "idxNm"));
                if (code == null) {
                    continue;
                }
                IndexQuoteUpsert row = toRow(code, item);
                if (row == null) {
                    dropped++;
                } else {
                    rows.add(row);
                }
            }

            int totalCount = body.path("totalCount").asInt(0);
            if (totalCount <= pageNo * portal.pageSize()) {
                break;
            }
            if (pageNo >= MAX_PAGES) {
                log.warn(
                        "지수 수집을 {}페이지에서 끊었다 — totalCount={} 라 남은 날짜가 빠졌다. 구간을 줄이거나 page-size 를 키워야 한다.",
                        MAX_PAGES,
                        totalCount);
                break;
            }
            pageNo++;
        }

        if (dropped > 0) {
            log.warn("지수 응답에서 쓸 수 없는 행 {}건을 버렸다(날짜 이상 또는 종가 없음).", dropped);
        }
        return rows;
    }

    private JsonNode requestPage(LocalDate from, LocalDate to, int pageNo) {
        // 인증키를 직접 인코딩하고 build(true) 로 넘긴다 — 주식시세 클라이언트와 같은 이유다.
        // 빌더에 맡기면 더하기 기호가 그대로 나가고 서버는 그것을 공백으로 읽는다.
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(PATH)
                .queryParam("serviceKey", URLEncoder.encode(portal.serviceKey(), StandardCharsets.UTF_8))
                .queryParam("resultType", "json")
                .queryParam("numOfRows", portal.pageSize())
                .queryParam("pageNo", pageNo)
                .queryParam("beginBasDt", BAS_DT.format(from))
                .queryParam("endBasDt", BAS_DT.format(to))
                .build(true)
                .toUri();

        String context = "beginBasDt=%s endBasDt=%s pageNo=%d".formatted(from, to, pageNo);
        byte[] raw;
        try {
            // 바이트로 받아 UTF-8 로 직접 해독한다 — 포털이 charset 을 빼먹는 일이 있다.
            raw = restClient.get().uri(uri).retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new PublicDataException("포털 호출이 실패했다 — " + context, e);
        }
        String body = raw == null ? null : new String(raw, StandardCharsets.UTF_8);
        return PublicDataJson.body(body, context);
    }

    /** 쓸 수 없는 행은 null 로 돌려 그 줄만 버린다 — 하루 때문에 구간 전체를 잃지 않는다. */
    private IndexQuoteUpsert toRow(IndexCode code, JsonNode item) {
        LocalDate tradeDate = tradeDate(item);
        BigDecimal close = decimal(item, "clpr");
        if (tradeDate == null || close == null) {
            return null;
        }
        return new IndexQuoteUpsert(code, tradeDate, close);
    }

    private LocalDate tradeDate(JsonNode item) {
        String basDt = text(item, "basDt");
        if (basDt == null) {
            return null;
        }
        try {
            return LocalDate.parse(basDt, BAS_DT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
