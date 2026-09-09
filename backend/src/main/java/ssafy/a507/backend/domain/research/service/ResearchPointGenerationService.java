package ssafy.a507.backend.domain.research.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.common.ai.AiClient;
import ssafy.a507.backend.common.ai.AiException;
import ssafy.a507.backend.common.ai.AiProperties;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.DailyQuoteRepository;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.entity.ResearchPoint;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;
import ssafy.a507.backend.domain.research.repository.ResearchPointRepository;

/**
 * 긍정·위험·확인 포인트 생성 (ANT-RESEARCH-04).
 *
 * <p><b>요청 시점에 만든다(2026-09-09 부터).</b> 원래는 배치 B6 가 매일 상장 종목 전부(≈300)를
 * 미리 만들었는데, 실제로 열리는 종목은 소수라 GMS 토큰이 하루 만에 바닥났다. 지금은 사용자가
 * 예측 탭에서 포인트를 요청한 종목만, 최신 거래일 기준으로 한 번 만들어 저장한다 — 같은 종목·
 * 같은 거래일의 다음 요청은 DB 에서 바로 나간다.
 *
 * <p>브리핑과 재료·기준일이 같다({@link StockMaterials}). 다른 것은 두 가지다.
 *
 * <p><b>① 응답을 JSON 배열로 받는다.</b> 브리핑은 줄글이라 첫 줄만 떼면 됐지만 포인트는 3열 × N건
 * 이고 건마다 근거 문서를 가리켜야 한다. 줄글에서 "이 문장의 근거는 몇 번 문서"를 뽑아내려면
 * 파서를 새로 쓰는 셈이라, 애초에 구조로 받는다.
 *
 * <p><b>② 건 단위로 버린다.</b> 브리핑은 한 건이 곧 한 응답이라 D16 에 걸리면 통째로 버렸다.
 * 포인트는 아홉 건 중 하나만 이상해도 나머지는 쓸 만하다 — 이상한 건만 버리고 남는 것을 저장한다.
 * 우리가 준 목록 밖의 {@code documentId} 를 지목한 건도 여기서 걸러진다("근거 보기"가 엉뚱한
 * 기사로 가는 것이 근거 없는 것보다 나쁘다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchPointGenerationService {

    /** 한 열(긍정·위험·확인)에 넣는 최대 건수. 화면이 카드 3장까지 보여 준다. */
    private static final int MAX_PER_KIND = 3;

    /** 재료로 넣는 원천별 문서 수. 뉴스 5 + 공시 5 면 프롬프트가 2천 자를 넘지 않는다. */
    private static final int DOCS_PER_SOURCE = 5;

    /** {@code research_points.body} 컬럼 길이. 넘는 건은 잘라 붙이지 않고 버린다. */
    private static final int MAX_BODY = 300;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 응답을 읽기만 하는 용도라 앱의 직렬화 설정을 물려받지 않는다(AiClient 와 같은 이유). */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String INSTRUCTION =
            """
            너는 한국 주식 리서치 화면의 "긍정 · 위험 · 확인" 3열 포인트를 만든다. 주어진 수치는
            서버가 계산한 값이다. 아래 규칙을 반드시 지켜라.

            - JSON 배열 하나만 출력한다. 설명·머리말·마크다운 코드펜스를 붙이지 않는다.
            - 원소는 {"kind": "POSITIVE" | "RISK" | "CHECK", "body": "...", "documentId": 숫자 또는 null} 이다.
            - kind 마다 2~3개씩, 전체 9개를 넘기지 않는다.
            - kind 의 뜻 — POSITIVE: 투자 판단에 우호적인 사실, RISK: 불리하거나 부담이 되는 사실,
              CHECK: 아직 결론이 나지 않아 앞으로 지켜볼 점.
            - body 는 한국어 평서문 한 문장, 120자 이내로 쓴다. 이모지·목록 기호를 쓰지 않는다.
            - documentId 는 아래 "문서" 목록에 있는 번호만 쓴다. 목록에 없는 번호를 지어내지 않는다.
              특정 문서가 아니라 시세·재무 수치에서 나온 포인트는 null 로 둔다.
            - 주어진 수치와 문서 내용에 있는 사실만 쓴다. 새 수치를 계산하거나 지어내지 않는다.
            - 매수·매도 권유, 목표주가, 주가 방향 예측을 쓰지 않는다.
            - 상승·하락의 확률이나 가능성을 수치로 쓰지 않는다. "확률"이라는 단어를 쓰지 않는다.
            - 등락률을 인용할 때는 "3.2% 상승했다"처럼 수치를 앞에 쓴다. "상승 3.2%" 처럼 방향을
              앞세우지 않는다.
            """;

    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final StockMaterials stockMaterials;
    private final ResearchPointRepository researchPointRepository;
    private final ResearchDocumentRepository researchDocumentRepository;
    private final DailyQuoteRepository dailyQuoteRepository;

    /**
     * 종목별 잠금. 두 사용자가 같은 종목을 같은 순간 열면 둘 다 "없음"으로 보고 두 번 부른다 — 락 안에서
     * 다시 확인한다. ponytail: 서버 인스턴스가 하나라 JVM 락으로 족하다. 스케일아웃하면 Redis 락으로.
     */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * 한 종목의 포인트를 최신 거래일 기준으로 만든다. 이미 있으면 부르지 않는다.
     *
     * @return 이번 호출로 저장한 행 수 · 이미 있거나 재료(키·시세)가 없으면 0
     * @throws AiException 생성 실패 — 요청 경로라 삼키지 않는다. 호출부가 사용자에게 알린다
     */
    public int generate(Stock stock) {
        if (!aiProperties.isConfigured()) {
            log.info("[POINT] AI_API_KEY 가 없어 포인트를 만들지 않는다 stock={}", stock.getCode());
            return 0;
        }
        LocalDate targetDate = dailyQuoteRepository.findLatestTradeDate().orElse(null);
        if (targetDate == null) {
            log.warn("[POINT] daily_quotes 가 비어 있어 포인트를 만들지 않는다 — 일봉 수집이 먼저다");
            return 0;
        }
        synchronized (locks.computeIfAbsent(stock.getCode(), code -> new Object())) {
            int written = generateStock(stock, targetDate);
            if (written > 0) {
                log.info("[POINT] {} · 기준일 {} · {}건 생성", stock.getCode(), targetDate, written);
            }
            return written;
        }
    }

    private int generateStock(Stock stock, LocalDate targetDate) {
        if (researchPointRepository.existsByStock_CodeAndTargetDate(stock.getCode(), targetDate)) {
            // 한 건이라도 있으면 넘어간다. 아홉 건 중 일부가 버려져 열이 비었어도 다시 부르지
            // 않는다 — 재시도를 열어 두면 포인트가 적게 나오는 종목을 매 회차 다시 불러 한도를
            // 태운다. 다음 영업일이 새 기준일로 다시 만든다.
            return 0;
        }
        String facts = stockMaterials.facts(stock, targetDate);
        if (facts == null) {
            // 그날 거래정지였거나 시세가 아직 안 들어온 종목. 재료 없이 만들면 지어낸 포인트가 된다.
            return 0;
        }
        Map<Long, ResearchDocument> documents = documents(stock.getCode(), targetDate);
        String input = facts + documentBlock(documents);

        List<ResearchPoint> points = parse(aiClient.complete(INSTRUCTION, input), stock, targetDate, documents);
        if (points.isEmpty()) {
            // 전부 버려졌으면 저장하지 않는다 — 빈 채로 두면 다음 요청이 다시 만들어 본다.
            log.warn("[POINT] 쓸 수 있는 포인트가 없어 저장하지 않는다 stock={}", stock.getCode());
            return 0;
        }
        researchPointRepository.saveAll(points);
        return points.size();
    }

    // ── 재료 ─────────────────────────────────────────────────

    /**
     * 근거로 지목할 수 있는 문서 — 뉴스와 DART 공시.
     *
     * <p>기준일 자정(KST) 이전 것만 본다. 브리핑과 같은 이유다 — 그날 저녁 들어온 D 기사가 D-1
     * 시세 재료에 붙으면 "급등 소식"과 마이너스 등락률이 한 프롬프트에 들어간다.
     */
    private Map<Long, ResearchDocument> documents(String stockCode, LocalDate targetDate) {
        Instant endOfTarget = targetDate.plusDays(1).atStartOfDay(KST).toInstant();
        Map<Long, ResearchDocument> documents = new LinkedHashMap<>();
        for (ResearchDocument.Source source : List.of(ResearchDocument.Source.NEWS, ResearchDocument.Source.DART)) {
            researchDocumentRepository
                    .findByStock_CodeAndSourceAndPublishedAtBeforeOrderByPublishedAtDesc(
                            stockCode, source, endOfTarget, Limit.of(DOCS_PER_SOURCE))
                    .forEach(d -> documents.put(d.getId(), d));
        }
        return documents;
    }

    private static String documentBlock(Map<Long, ResearchDocument> documents) {
        if (documents.isEmpty()) {
            return "문서: (없음)\n";
        }
        StringBuilder sb = new StringBuilder("문서(번호 · 날짜 · 원천 · 내용):\n");
        documents.values().forEach(d -> sb.append("- [").append(d.getId()).append("] ")
                .append(d.getPublishedAt().atZone(KST).toLocalDate())
                .append(" · ").append(d.getSource() == ResearchDocument.Source.DART ? "공시" : "뉴스")
                .append(" · ").append(d.excerpt())
                .append('\n'));
        return sb.toString();
    }

    // ── 파싱 ─────────────────────────────────────────────────

    /** 배열 밖의 군말은 무시하고, 규칙을 어긴 원소는 그 건만 버린다. */
    private List<ResearchPoint> parse(
            String text, Stock stock, LocalDate targetDate, Map<Long, ResearchDocument> documents) {
        JsonNode array = array(text, stock.getCode());
        if (array == null) {
            return List.of();
        }
        Map<ResearchPoint.Kind, Integer> counts = new EnumMap<>(ResearchPoint.Kind.class);
        List<ResearchPoint> points = new ArrayList<>();
        for (JsonNode node : array) {
            ResearchPoint.Kind kind = kind(node.path("kind").asText(""));
            String body = node.path("body").asText("").trim();
            if (kind == null || body.isEmpty() || body.length() > MAX_BODY) {
                log.debug("[POINT] 포인트 한 건을 버린다 — kind·body 규칙 위반 stock={}", stock.getCode());
                continue;
            }
            if (BriefingGenerationService.FORBIDDEN.matcher(body).find()) {
                log.warn("[POINT] D16 위반 문구가 있어 포인트 한 건을 버린다 stock={}", stock.getCode());
                continue;
            }
            Long documentId = documentId(node.path("documentId"));
            ResearchDocument document = documentId == null ? null : documents.get(documentId);
            if (documentId != null && document == null) {
                // 우리가 주지 않은 번호다. 근거만 지우고 살리면 "근거 보기"가 없는 카드가 되는데,
                // 모델이 없는 문서를 지어냈다는 것은 body 도 그 문서를 근거로 썼다는 뜻이다.
                log.warn("[POINT] 목록 밖 documentId={} 라 포인트 한 건을 버린다 stock={}", documentId, stock.getCode());
                continue;
            }
            if (counts.merge(kind, 1, Integer::sum) > MAX_PER_KIND) {
                continue;
            }
            points.add(ResearchPoint.of(stock, targetDate, kind, body, document));
        }
        return points;
    }

    /** 코드펜스나 인사말이 붙어 와도 첫 {@code [} 부터 마지막 {@code ]} 까지를 배열로 읽는다. */
    private JsonNode array(String text, String stockCode) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            log.warn("[POINT] JSON 배열이 없는 응답이라 포인트를 버린다 stock={}", stockCode);
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(text.substring(start, end + 1));
            return root.isArray() ? root : null;
        } catch (JsonProcessingException e) {
            log.warn("[POINT] 포인트 응답을 읽지 못했다 stock={} — {}", stockCode, e.getMessage());
            return null;
        }
    }

    /**
     * 번호로 읽히는 값은 모양을 가리지 않고 받는다.
     *
     * <p>모델이 {@code "documentId": "42"}(문자열)나 {@code 42.0}(실수)로 싸서 내는 일이 흔하다.
     * 정수만 받으면 그런 건이 "근거 없는 종합 포인트"로 저장돼 사용자가 근거 링크를 잃는다 —
     * 목록 검사까지 태워야 살릴 건은 살리고 지어낸 번호는 버린다.
     */
    private static Long documentId(JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.valueOf(node.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static ResearchPoint.Kind kind(String value) {
        for (ResearchPoint.Kind kind : ResearchPoint.Kind.values()) {
            if (kind.name().equalsIgnoreCase(value.trim())) {
                return kind;
            }
        }
        return null;
    }
}
