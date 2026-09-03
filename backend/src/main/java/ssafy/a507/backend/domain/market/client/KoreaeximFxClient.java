package ssafy.a507.backend.domain.market.client;

import static ssafy.a507.backend.domain.market.client.PublicDataJson.decimal;
import static ssafy.a507.backend.domain.market.client.PublicDataJson.text;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 한국수출입은행 환율 API 에서 하루치 원/달러 매매기준율을 받아 온다.
 *
 * <p><b>날짜별이다 — 구간 조회가 없다.</b> {@code searchdate} 하나에 그날 통화 전부가 온다.
 * 일 1,000콜 한도라 2020년부터 채우려면 이틀이 걸리고, 한도에 걸리면 {@code result 4} 가
 * 온다. 그 경우는 따로 알아볼 수 있는 예외로 올려 호출 쪽이 그 회차를 멈추게 한다 — 남은
 * 날짜를 계속 두드려 봐야 전부 같은 답이다.
 *
 * <p>환율에는 "종가" 가 없다. 여기서 받는 값은 그날 처음 고시된 매매기준율({@code deal_bas_r})
 * 이고, 우리는 그것을 그날의 값으로 쓴다. 고시 시각은 영업일 11시 전후라 13시 이후 회차에서는
 * 어제치는 물론 오늘치도 받을 수 있지만, 지수·종가와 기준일을 맞추려고 어제까지만 받는다.
 *
 * <p><b>전용 신뢰 묶음을 붙이는 이유.</b> 이 서버는 TLS 핸드셰이크에 중간 인증서(Thawte TLS RSA
 * CA G1)를 실어 보내지 않는다. 브라우저와 curl 은 AIA 로 받아와 통과하지만 JDK 는 경로를 못
 * 만들어 PKIX 오류로 끝난다(2026-09-03 확인 — {@code enableAIAcaIssuers} 로도 실패). 그래서
 * 중간 인증서를 {@code certs/koreaexim-ca.crt} 에 동봉하고 이 클라이언트에만 그 묶음을 단다.
 * 다른 외부 호출은 JDK 기본 신뢰 저장소 그대로다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(KoreaeximProperties.class)
public class KoreaeximFxClient {

    /** 수출입은행이 하루 호출 한도를 넘겼다. 이번 회차는 여기서 멈추고 내일 이어서 받는다. */
    public static class DailyLimitExceededException extends PublicDataException {
        public DailyLimitExceededException(String message) {
            super(message);
        }
    }

    /** application.yaml 의 {@code spring.ssl.bundle.pem.koreaexim}. */
    static final String SSL_BUNDLE = "koreaexim";

    private static final DateTimeFormatter SEARCH_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** AP01 = 환율. 나머지(AP02 대출금리 · AP03 국제금리)는 쓰지 않는다. */
    private static final String DATA_TYPE = "AP01";

    private static final String USD = "USD";

    /** 배열 첫 원소의 result. 1 성공 · 2 DATA 코드 오류 · 3 인증키 오류 · 4 일 한도 초과. */
    private static final int RESULT_OK = 1;

    private static final int RESULT_DAILY_LIMIT = 4;

    private final RestClient restClient;
    private final KoreaeximProperties properties;

    public KoreaeximFxClient(
            RestClient.Builder restClientBuilder, RestClientSsl ssl, KoreaeximProperties properties) {
        this.restClient = restClientBuilder.apply(ssl.fromBundle(SSL_BUNDLE)).build();
        this.properties = properties;
    }

    /**
     * 그날의 원/달러 매매기준율. 주말·공휴일처럼 고시가 없는 날은 빈 값이다 — 오류가 아니다.
     *
     * @throws DailyLimitExceededException 일 호출 한도를 넘겼을 때
     * @throws PublicDataException 그 밖의 호출·응답 실패
     */
    public Optional<BigDecimal> fetchUsd(LocalDate date) {
        String searchDate = SEARCH_DATE.format(date);
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .queryParam("authkey", properties.authKey())
                .queryParam("searchdate", searchDate)
                .queryParam("data", DATA_TYPE)
                .build()
                .toUri();

        String context = "searchdate=" + searchDate;
        byte[] raw;
        try {
            // 바이트로 받아 UTF-8 로 직접 해독한다 — 통화명이 한글이라 charset 이 빠지면 깨진다.
            raw = restClient.get().uri(uri).retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new PublicDataException("수출입은행 호출이 실패했다 — " + context, e);
        }
        String body = raw == null ? null : new String(raw, StandardCharsets.UTF_8);
        JsonNode root = PublicDataJson.parse(body, context);

        if (!root.isArray()) {
            throw new PublicDataException("수출입은행 응답이 배열이 아니다 — " + context);
        }
        if (root.isEmpty()) {
            return Optional.empty();
        }

        // 오류도 배열 안에 한 원소로 온다. 통화 줄이 아니라 result 만 채워진 줄이다.
        int result = root.get(0).path("result").asInt(0);
        if (result == RESULT_DAILY_LIMIT) {
            throw new DailyLimitExceededException("수출입은행 일 호출 한도를 넘겼다 — " + context);
        }
        if (result != RESULT_OK) {
            throw new PublicDataException(
                    "수출입은행이 오류를 돌려줬다 — %s result=%d".formatted(context, result));
        }

        for (JsonNode item : root) {
            if (USD.equals(text(item, "cur_unit"))) {
                return Optional.ofNullable(decimal(item, "deal_bas_r"));
            }
        }
        log.warn("수출입은행 응답에 USD 줄이 없다 — {}", context);
        return Optional.empty();
    }
}
