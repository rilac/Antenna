package ssafy.a507.backend.domain.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.domain.research.client.NaverNewsClient;
import ssafy.a507.backend.domain.research.client.NaverNewsException;
import ssafy.a507.backend.domain.research.client.NaverNewsItem;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * 뉴스 수집의 재실행 안전성과 걸러내기를 본다.
 *
 * <p><b>멱등 키가 DART 와 다르다.</b> 뉴스 검색은 고유 ID 를 주지 않아 원문 주소를 해시해
 * 쓰는데, 그 계산이 어긋나면 매일 도는 배치가 같은 기사를 날마다 새로 쌓는다.
 *
 * <p>네이버는 스텁이다 — 테스트는 외부와 통신하지 않는다.
 */
@SpringBootTest(properties = {"app.naver-news.key-id=test-id", "app.naver-news.key=test-key"})
@Transactional
@DisplayName("뉴스 수집")
class NewsIngestServiceTest {

    private static final String SAMSUNG = "005930";
    private static final String URL = "https://news.example.com/article?id=1";

    @Autowired NewsIngestService ingestService;
    @Autowired EntityManager em;
    @Autowired ResearchDocumentRepository researchDocumentRepository;

    @MockitoBean NaverNewsClient newsClient;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, SAMSUNG)
                .setParameter(2, "삼성전자")
                .executeUpdate();
        em.createNativeQuery("INSERT INTO stocks (code, name, listed) VALUES (?, ?, TRUE)")
                .setParameter(1, "035720")
                .setParameter(2, "카카오")
                .executeUpdate();
        em.flush();
    }

    @Test
    @DisplayName("같은 기사를 다시 받아도 한 건이다 — 원문 주소 해시가 멱등 키다")
    void 재수집_멱등() {
        given(newsClient.searchLatest("삼성전자"))
                .willReturn(List.of(item("삼성전자, 신공장 착공", URL)));

        int first = ingestService.ingestNews();
        em.flush();
        int second = ingestService.ingestNews();
        em.flush();

        assertThat(first).isEqualTo(1);
        assertThat(second).as("두 번째 회차는 새로 쌓을 게 없다").isZero();
        assertThat(researchDocumentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("한 응답에 같은 주소가 두 번 와도 한 건만 쌓인다")
    void 같은_회차_중복() {
        given(newsClient.searchLatest("삼성전자"))
                .willReturn(List.of(item("삼성전자 실적", URL), item("삼성전자 실적 [재송]", URL)));

        assertThat(ingestService.ingestNews()).isEqualTo(1);
    }

    @Test
    @DisplayName("제목에 종목명이 없는 기사는 버린다 — 종목명 검색은 무관 기사를 데려온다")
    void 노이즈_필터() {
        given(newsClient.searchLatest("삼성전자"))
                .willReturn(List.of(
                        item("삼성전자 신제품 공개", URL),
                        item("프로야구 한화 이글스 승리", "https://news.example.com/article?id=2")));

        assertThat(ingestService.ingestNews()).isEqualTo(1);
        assertThat(researchDocumentRepository.findAll())
                .extracting(ResearchDocument::getTitle)
                .containsExactly("삼성전자 신제품 공개");
    }

    @Test
    @DisplayName("매체가 띄어 쓴 회사명도 같은 것으로 본다")
    void 공백_차이는_무시() {
        assertThat(NewsIngestService.mentions("삼성 전자, 신공장 착공", "삼성전자")).isTrue();
        assertThat(NewsIngestService.mentions("현대차 판매 호조", "삼성전자")).isFalse();
    }

    @Test
    @DisplayName("스포츠 기사를 가려낸다 — 두산·한화·KT·LG·기아·NC 는 구단 이름이기도 하다")
    void 스포츠_기사_필터() {
        assertThat(NewsIngestService.isNoise("두산 선발 최승용, 3이닝 4실점으로 강판", null)).isTrue();
        assertThat(NewsIngestService.isNoise("한화, 9회말 끝내기", "홈런으로 연승")).isTrue();
        assertThat(NewsIngestService.isNoise("유안타증권 오픈 골프 개막", "")).isTrue();
        assertThat(NewsIngestService.isNoise("두산에너빌리티, 원전 수주", "체코 원전 계약")).isFalse();
        assertThat(NewsIngestService.isNoise("경기 침체 우려에 한화 하락", "안타까운 실적")).isFalse();
        assertThat(NewsIngestService.isNoise("KT, 2분기 영업이익 증가", null)).isFalse();
    }

    @Test
    @DisplayName("되돌아보기 구간보다 오래된 기사는 버린다")
    void 오래된_기사() {
        given(newsClient.searchLatest("삼성전자"))
                .willReturn(List.of(new NaverNewsItem(
                        "삼성전자 옛 기사",
                        URL,
                        "발췌",
                        Instant.now().minus(400, ChronoUnit.DAYS))));

        assertThat(ingestService.ingestNews()).isZero();
    }

    @Test
    @DisplayName("발췌를 함께 남긴다 — 요약을 다시 만들 때의 유일한 재료다")
    void 발췌_저장() {
        given(newsClient.searchLatest("삼성전자"))
                .willReturn(List.of(item("삼성전자, 신공장 착공", URL)));

        ingestService.ingestNews();
        em.flush();

        assertThat(researchDocumentRepository.findAll())
                .singleElement()
                .satisfies(document -> {
                    assertThat(document.getSnippet()).isEqualTo("발췌 본문");
                    assertThat(document.getExternalId())
                            .as("주소 해시는 64자 고정 — external_id 컬럼이 100자다")
                            .hasSize(64);
                });
    }

    @Test
    @DisplayName("끝의 슬래시만 다른 주소는 같은 기사다")
    void 주소_정규화() {
        assertThat(NewsIngestService.externalId("https://a.com/b/"))
                .isEqualTo(NewsIngestService.externalId("https://a.com/b"));
    }

    @Test
    @DisplayName("한도 초과(429)면 남은 종목을 부르지 않고 회차를 접는다")
    void 한도_초과는_회차_중단() {
        given(newsClient.searchLatest(anyString()))
                .willThrow(new NaverNewsException(
                        "뉴스 검색 실패",
                        HttpClientErrorException.create(
                                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                                HttpHeaders.EMPTY, new byte[0], null)));

        assertThat(ingestService.ingestNews()).isZero();

        verify(newsClient, times(1)).searchLatest(anyString());
    }

    @Test
    @DisplayName("일시적 실패(5xx)는 그 종목만 건너뛰고 다음 종목으로 간다")
    void 일시_실패는_종목_격리() {
        given(newsClient.searchLatest(anyString()))
                .willThrow(new NaverNewsException(
                        "뉴스 검색 실패",
                        HttpClientErrorException.create(
                                HttpStatus.BAD_GATEWAY, "Bad Gateway",
                                HttpHeaders.EMPTY, new byte[0], null)));

        ingestService.ingestNews();

        verify(newsClient, times(2)).searchLatest(anyString());
    }

    private static NaverNewsItem item(String title, String url) {
        return new NaverNewsItem(title, url, "발췌 본문", Instant.now().minus(1, ChronoUnit.HOURS));
    }
}
