package ssafy.a507.backend.domain.research.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 네이버 뉴스 검색 호출 (ANT-RESEARCH-02).
 *
 * <p>DART 와 달리 실패가 HTTP 상태로 온다 — 한도를 넘기면 429, 자격증명이 틀리면 401 이다.
 * 그래서 본문 봉투를 먼저 뜯을 필요가 없고, 상태 코드만 보고 갈라도 된다.
 *
 * <p><b>제목과 발췌에 마크업이 섞여 온다.</b> 검색어가 {@code <b>} 로 감싸여 있고 따옴표는
 * {@code &quot;} 로 이스케이프된다. 그대로 저장하면 화면에 태그가 그대로 뜨거나, 프롬프트에
 * 실려 요약문에까지 새어 든다. 여기서 걷어 내고 내보낸다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(NaverNewsProperties.class)
public class NaverNewsClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** {@code pubDate} 는 RFC 1123 이다 — 예: {@code Thu, 03 Sep 2026 15:05:45 +0900}. */
    private static final DateTimeFormatter PUB_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestClient restClient;
    private final NaverNewsProperties properties;

    @Autowired
    public NaverNewsClient(RestClient.Builder restClientBuilder, NaverNewsProperties properties) {
        this(restClientBuilder.requestFactory(timeoutAwareFactory()).build(), properties);
    }

    /** 테스트에서 스텁 RestClient 를 끼우는 통로. */
    NaverNewsClient(RestClient restClient, NaverNewsProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static JdkClientHttpRequestFactory timeoutAwareFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /**
     * 최신순 검색. 페이징하지 않는다 — 매일 도는 배치라 한 회차가 집을 것은 어제 하루치이고,
     * {@code display} 한 장이면 덮인다. 되돌아보기는 회차를 겹쳐 도는 것으로 충분하다.
     *
     * @param query 검색어(종목명)
     * @return 발행 최신순 · 파싱하지 못한 건은 빠진다
     */
    public List<NaverNewsItem> searchLatest(String query) {
        // URI 로 넘긴다. 인코딩한 문자열을 String 오버로드에 주면 RestClient 가 한 번 더
        // 인코딩해 %EC 가 %25EC 가 된다 — 종목명이 한글이라 매 호출이 빈손으로 돌아온다.
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/news")
                .queryParam("query", query)
                .queryParam("display", properties.displayPerStock())
                .queryParam("sort", "date")
                .encode()
                .build()
                .toUri();

        String response;
        try {
            response = restClient
                    .get()
                    .uri(uri)
                    .header("X-NCP-APIGW-API-KEY-ID", properties.keyId())
                    .header("X-NCP-APIGW-API-KEY", properties.key())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new NaverNewsException("뉴스 검색 실패 [" + query + "] — " + e.getMessage(), e);
        }

        return parse(response);
    }

    private List<NaverNewsItem> parse(String response) {
        JsonNode items;
        try {
            items = objectMapper.readTree(response == null ? "" : response).path("items");
        } catch (Exception e) {
            throw new NaverNewsException("뉴스 응답을 읽지 못했다 — " + e.getMessage(), e);
        }

        List<NaverNewsItem> parsed = new ArrayList<>();
        for (JsonNode item : items) {
            Instant publishedAt = parsePubDate(item.path("pubDate").asText(null));
            String originUrl = item.path("originallink").asText("");
            if (publishedAt == null || originUrl.isBlank()) {
                // 발행 시각이 없으면 정렬도 되돌아보기도 못 한다. 주소가 없으면 멱등 키를
                // 만들 수 없다. 둘 중 하나라도 없으면 저장할 수 있는 행이 아니다.
                continue;
            }
            parsed.add(new NaverNewsItem(
                    plainText(item.path("title").asText("")),
                    originUrl.trim(),
                    plainText(item.path("description").asText("")),
                    publishedAt));
        }
        return parsed;
    }

    private static Instant parsePubDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.from(PUB_DATE.parse(raw.trim()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 태그와 엔티티를 걷어 낸다. 일반 HTML 파서를 붙이지 않은 이유 — 여기 오는 마크업은
     * 검색어 강조 {@code <b>} 한 종류뿐이고, 엔티티도 네이버가 쓰는 몇 개로 끝난다.
     * {@code &amp;} 를 마지막에 푸는 것이 중요하다: 먼저 풀면 {@code &amp;lt;} 가 두 번
     * 풀려 {@code <} 가 되어 버린다.
     */
    static String plainText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("<[^>]*>", "")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .trim();
    }
}
