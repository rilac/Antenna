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
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ssafy.a507.backend.domain.market.entity.Stock;

/**
 * 「금융위원회_주식시세정보」에서 하루치 일봉을 통째로 받아 온다.
 *
 * <p><b>종목별이 아니라 날짜별이다.</b> basDt 한 번이면 그날 전 종목이 온다. 종목마다 부르면
 * 하루에 2,800콜이 나가 일 1만 콜 한도로는 3년치 백필이 불가능하다.
 *
 * <p>WebClient 가 아니라 RestClient 인 이유 — 수집은 스케줄러 스레드에서 도는 블로킹 배치라
 * 논블로킹으로 얻는 것이 없고, 이 저장소의 외부 호출은 이미 RestClient 로 통일돼 있다
 * (SsafyOAuthClient). 페이지도 totalCount 를 받아야 다음을 알 수 있어 어차피 순차다.
 *
 * <p>응답을 레코드로 바로 바인딩하지 않고 트리로 읽는다. 포털은 데이터가 없는 날 items 를
 * 빈 문자열로 주고, 한 건뿐이면 배열 대신 객체로 주며, 인증키가 틀리면 200 에 XML 오류
 * 문서를 실어 보낸다. 고정된 모양을 가정하면 그 셋 중 하나에서 매번 깨진다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(PublicDataProperties.class)
public class PublicDataStockClient {

    private static final String PATH = "/getStockPriceInfo";
    private static final DateTimeFormatter BAS_DT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 종목 수는 3천 안팎이다. 페이지 크기를 잘못 잡아도 무한히 돌지 않게 상한을 둔다. */
    private static final int MAX_PAGES = 50;

    /** stocks.name 컬럼 폭. 넘치면 배치 전체가 깨지므로 잘라서라도 넣는다(표시 전용 값). */
    private static final int MAX_NAME_LENGTH = 60;

    private final RestClient restClient;
    private final PublicDataProperties properties;

    public PublicDataStockClient(
            RestClient.Builder restClientBuilder, PublicDataProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    /**
     * 하루치를 받는다. 공휴일·휴장이면 빈 목록이다 — 오류가 아니라 정상이다.
     *
     * <p>수집 범위 설정(market-filter · top-count)이 있으면 여기서 적용한다. 시장은 포털에
     * {@code mrktCls} 로도 보내지만 응답에서 한 번 더 거른다 — 파라미터가 무시돼도 결과가
     * 틀어지지 않게. 상위 N 은 포털에 잘라 주는 파라미터가 없어 받은 뒤 시가총액으로 고른다.
     *
     * @throws PublicDataException 호출·응답이 실패했을 때. 그날은 수집하지 못한 것으로 남는다.
     */
    public List<StockPriceRow> fetchDay(LocalDate baseDate) {
        List<StockPriceRow> rows = new ArrayList<>();
        int dropped = 0;
        int pageNo = 1;

        while (true) {
            JsonNode body = requestPage(baseDate, pageNo);

            for (JsonNode item : items(body)) {
                StockPriceRow row = toRow(item, baseDate);
                if (row == null) {
                    dropped++;
                } else {
                    rows.add(row);
                }
            }

            int totalCount = body.path("totalCount").asInt(0);
            if (totalCount <= pageNo * properties.pageSize()) {
                break;
            }
            if (pageNo >= MAX_PAGES) {
                log.warn(
                        "{} 수집을 {}페이지에서 끊었다 — totalCount={} 라 남은 종목이 빠졌다. page-size 를 키워야 한다.",
                        baseDate,
                        MAX_PAGES,
                        totalCount);
                break;
            }
            pageNo++;
        }

        if (dropped > 0) {
            log.warn("{} 응답에서 쓸 수 없는 행 {}건을 버렸다(종목코드 이상 또는 종가 없음).", baseDate, dropped);
        }
        return select(rows, baseDate);
    }

    /**
     * 수집 범위 결정(예: KOSPI 시가총액 상위 300)의 실행 지점.
     *
     * <p>상위 N 을 날짜마다 그날의 시가총액으로 다시 고른다 — 백필에서는 날짜에 따라 경계
     * 근처 종목이 들고나며, 그런 종목의 시계열에는 빈 날이 생길 수 있다. 데이터 양을 줄이려는
     * 결정이라 그 흔들림은 감수한다.
     */
    private List<StockPriceRow> select(List<StockPriceRow> rows, LocalDate baseDate) {
        List<StockPriceRow> selected = rows;

        Stock.Market filter = properties.marketFilter();
        if (filter != null) {
            // 시장을 모르는 행(market null)도 함께 떨어진다 — 필터를 켰다면 확인된 것만 남긴다.
            selected = selected.stream().filter(row -> row.market() == filter).toList();
        }

        int top = properties.topCount();
        if (top > 0 && selected.size() > top) {
            selected = selected.stream()
                    .sorted(Comparator.comparing(
                            StockPriceRow::marketCap,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(top)
                    .toList();
        }

        if (selected.size() < rows.size()) {
            log.info(
                    "{} 수집 범위를 좁혔다 — 전체 {}건 중 {}건(market={}, top={})",
                    baseDate,
                    rows.size(),
                    selected.size(),
                    filter,
                    top);
        }
        return selected;
    }

    private JsonNode requestPage(LocalDate baseDate, int pageNo) {
        // 인증키를 직접 인코딩하고 build(true) 로 넘긴다. 빌더에 맡기면 더하기 기호를 그대로
        // 두는데, 서버는 쿼리의 그 문자를 공백으로 읽어 등록되지 않은 키가 된다.
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(PATH)
                .queryParam(
                        "serviceKey",
                        URLEncoder.encode(properties.serviceKey(), StandardCharsets.UTF_8))
                .queryParam("resultType", "json")
                .queryParam("numOfRows", properties.pageSize())
                .queryParam("pageNo", pageNo)
                .queryParam("basDt", BAS_DT.format(baseDate));
        if (properties.marketFilter() != null) {
            // 서버가 걸러 주면 하루가 3콜에서 1콜로 준다. 응답에서 한 번 더 거르므로
            // 이 파라미터가 무시돼도 결과는 같다.
            builder.queryParam("mrktCls", properties.marketFilter().name());
        }
        URI uri = builder.build(true).toUri();

        byte[] raw;
        try {
            // 문자열이 아니라 바이트로 받는다. 포털은 Content-Type 에 charset 을 빼먹는 일이
            // 있는데, 그러면 스프링이 ISO-8859-1 로 읽어 종목명이 통째로 깨진다. 본문은 항상
            // UTF-8 이므로 우리가 직접 해독하는 편이 확실하다.
            raw = restClient.get().uri(uri).retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new PublicDataException(
                    "포털 호출이 실패했다 — basDt=%s pageNo=%d".formatted(baseDate, pageNo), e);
        }
        String body = raw == null ? null : new String(raw, StandardCharsets.UTF_8);
        return PublicDataJson.body(body, "basDt=%s pageNo=%d".formatted(baseDate, pageNo));
    }

    /** 쓸 수 없는 행은 null 로 돌려 그 줄만 버린다 — 한 종목 때문에 하루를 통째로 잃지 않는다. */
    private StockPriceRow toRow(JsonNode item, LocalDate baseDate) {
        String code = text(item, "srtnCd");
        if (code == null || code.length() != 6) {
            return null;
        }
        BigDecimal close = decimal(item, "clpr");
        if (close == null) {
            // 종가는 판정·표시가 쓰는 유일한 가격이라 없으면 저장할 이유가 없다.
            return null;
        }

        return new StockPriceRow(
                code,
                name(item),
                market(text(item, "mrktCtg")),
                tradeDate(item, baseDate),
                decimal(item, "mkp"),
                decimal(item, "hipr"),
                decimal(item, "lopr"),
                close,
                volume(item),
                longValue(item, "lstgStCnt"),
                decimal(item, "mrktTotAmt"));
    }

    private String name(JsonNode item) {
        String name = text(item, "itmsNm");
        if (name == null) {
            return "";
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    private Stock.Market market(String code) {
        if (code == null) {
            return null;
        }
        for (Stock.Market market : Stock.Market.values()) {
            if (market.name().equalsIgnoreCase(code)) {
                return market;
            }
        }
        // 새 시장 구분이 생기면 값이 빈 채로 지나간다. upsert 가 COALESCE 라 기존 값은 지키고,
        // 로그로 남겨 enum 을 넓힐지 판단한다.
        log.warn("모르는 시장 구분이다 — mrktCtg={}", code);
        return null;
    }

    private LocalDate tradeDate(JsonNode item, LocalDate baseDate) {
        String basDt = text(item, "basDt");
        if (basDt == null) {
            return baseDate;
        }
        try {
            return LocalDate.parse(basDt, BAS_DT);
        } catch (DateTimeParseException e) {
            return baseDate;
        }
    }

    private Long volume(JsonNode item) {
        return longValue(item, "trqu");
    }

    private static Long longValue(JsonNode item, String field) {
        BigDecimal value = decimal(item, field);
        return value == null ? null : value.longValue();
    }
}
