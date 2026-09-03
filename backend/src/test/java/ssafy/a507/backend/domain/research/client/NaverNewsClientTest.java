package ssafy.a507.backend.domain.research.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 네이버 뉴스 검색 응답의 실제 모양을 고정해 둔다. 아래 본문은 2026-09-03 에 API HUB 로
 * 실제 호출해 받은 형태다 — 제목·발췌에 {@code <b>} 강조가 박혀 오는 것, 따옴표가
 * {@code &quot;} 로 이스케이프되는 것, {@code link} 와 {@code originallink} 가 같은 값으로
 * 오는 것(네이버뉴스 링크가 아니라 언론사 원문이다), {@code pubDate} 가 RFC 1123 인 것.
 *
 * <p>정제를 여기서 막지 못하면 태그가 그대로 DB 에 들어가고, 요약 프롬프트에 실려 생성된
 * 문장에까지 새어 든다.
 */
@DisplayName("네이버 뉴스 검색")
class NaverNewsClientTest {

    private static final MediaType JSON =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private static final String NEWS =
            """
            {"lastBuildDate":"Thu, 03 Sep 2026 15:05:45 +0900","total":4448871,"start":1,"display":3,
             "items":[
              {"title":"[종합] <b>삼성전자</b>·SK하이닉스發 &quot;성과급&quot; 확산",
               "originallink":"https://www.joongangenews.com/news/articleView.html?idxno=545350",
               "link":"https://www.joongangenews.com/news/articleView.html?idxno=545350",
               "description":"고용노동부가 <b>삼성전자</b> 등 대기업 노동조합을 중심으로 &amp; 확산한 요구와",
               "pubDate":"Thu, 03 Sep 2026 14:20:00 +0900"},
              {"title":"발행 시각이 없는 기사","originallink":"https://example.com/a",
               "link":"https://example.com/a","description":"발췌","pubDate":""},
              {"title":"원문 주소가 없는 기사","originallink":"",
               "link":"https://n.news.naver.com/b","description":"발췌",
               "pubDate":"Thu, 03 Sep 2026 09:00:00 +0900"}]}
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private NaverNewsClient client() {
        return new NaverNewsClient(
                builder.build(),
                new NaverNewsProperties("key-id", "key", null, 20, 14));
    }

    @Test
    @DisplayName("태그와 엔티티를 걷어 내고, 시각·주소가 없는 건은 버린다")
    void 검색_결과_정제() {
        server.expect(requestTo(
                        "https://naverapihub.apigw.ntruss.com/search/v1/news"
                                + "?query=%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90&display=20&sort=date"))
                .andExpect(header("X-NCP-APIGW-API-KEY-ID", "key-id"))
                .andExpect(header("X-NCP-APIGW-API-KEY", "key"))
                .andRespond(withSuccess(NEWS, JSON));

        List<NaverNewsItem> items = client().searchLatest("삼성전자");

        assertThat(items).as("발행 시각과 원문 주소가 있어야 저장할 수 있는 행이다").hasSize(1);
        NaverNewsItem item = items.get(0);
        assertThat(item.title()).isEqualTo("[종합] 삼성전자·SK하이닉스發 \"성과급\" 확산");
        assertThat(item.snippet()).isEqualTo("고용노동부가 삼성전자 등 대기업 노동조합을 중심으로 & 확산한 요구와");
        assertThat(item.originUrl())
                .isEqualTo("https://www.joongangenews.com/news/articleView.html?idxno=545350");
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-09-03T05:20:00Z"));
        server.verify();
    }

    @Test
    @DisplayName("DART 와 달리 실패가 HTTP 상태로 온다 — 401·429 는 예외다")
    void 인증_실패는_예외() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/news"))).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client().searchLatest("삼성전자"))
                .isInstanceOf(NaverNewsException.class)
                .hasMessageContaining("삼성전자");
    }

    @Test
    @DisplayName("&amp; 는 마지막에 푼다 — 먼저 풀면 &amp;lt; 가 두 번 풀린다")
    void 엔티티_해제_순서() {
        assertThat(NaverNewsClient.plainText("A &amp;lt;b&amp;gt; B")).isEqualTo("A &lt;b&gt; B");
    }
}
