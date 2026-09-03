package ssafy.a507.backend.domain.research.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.client.NaverNewsClient;
import ssafy.a507.backend.domain.research.client.NaverNewsException;
import ssafy.a507.backend.domain.research.client.NaverNewsItem;
import ssafy.a507.backend.domain.research.client.NaverNewsProperties;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * 뉴스 수집 (ANT-RESEARCH-02).
 *
 * <p>DART 수집과 같은 뼈대다 — 회차를 한 트랜잭션으로 묶지 않고, 종목 하나의 실패가 나머지를
 * 끌고 내려가지 않게 한다. {@code ingest_runs} 를 쓰지 않는 이유도 같다: 원천 URL 해시가
 * 유니크라 재실행 멱등이 이미 보장되고, 실패한 종목은 다음 회차가 다시 집는다.
 *
 * <p><b>다른 점은 멱등 키다.</b> DART 는 접수번호를 주지만 뉴스 검색은 고유 ID 를 주지 않는다.
 * 그래서 원문 주소를 해시해 {@code external_id} 로 쓴다. 같은 기사가 여러 매체에 전재되면
 * 주소가 달라 각각 남는데, 그건 실제로 다른 기사이므로 합치지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsIngestService {

    /** {@code research_documents.title} 컬럼 폭. */
    private static final int MAX_TITLE_LENGTH = 200;

    /** {@code research_documents.origin_url} 컬럼 폭. 넘는 주소는 잘라 쓸 수 없어 버린다. */
    private static final int MAX_URL_LENGTH = 500;

    private final NaverNewsClient newsClient;
    private final NaverNewsProperties properties;
    private final StockRepository stockRepository;
    private final ResearchDocumentRepository researchDocumentRepository;

    /**
     * 종목별 최신 뉴스 수집.
     *
     * @return 새로 쌓인 문서 수
     */
    public int ingestNews() {
        if (!properties.isConfigured()) {
            log.info("[NEWS] NAVER_API_KEY 가 없어 뉴스 수집을 건너뛴다");
            return 0;
        }

        List<Stock> targets = stockRepository.findAll().stream()
                .filter(Stock::isListed)
                .toList();
        if (targets.isEmpty()) {
            log.warn("[NEWS] stocks 가 비어 있어 뉴스 수집을 건너뛴다 — 일봉 수집이 먼저다");
            return 0;
        }

        Instant since = Instant.now().minus(Duration.ofDays(properties.lookbackDays()));
        int created = 0;
        for (Stock stock : targets) {
            try {
                List<NaverNewsItem> items = newsClient.searchLatest(stock.getName()).stream()
                        .filter(item -> item.publishedAt().isAfter(since))
                        .filter(item -> mentions(item.title(), stock.getName()))
                        .toList();
                created += save(stock, items);
            } catch (NaverNewsException e) {
                if (e.abortsRun()) {
                    // 자격증명 오류나 한도 초과는 다음 종목도 똑같이 실패한다. 남은 종목을
                    // 돌면 경고 300줄과 헛호출 300번만 남는다. 회차를 접고 다음 날을 기다린다.
                    log.warn("[NEWS] 수집 회차를 접는다 ({}건 반영) — {}", created, e.getMessage());
                    break;
                }
                log.warn("[NEWS] 수집 실패 {} — {}", stock.getCode(), e.getMessage());
            } catch (DataAccessException e) {
                log.warn("[NEWS] 저장 실패 {} — {}", stock.getCode(), e.getMessage());
            }
        }
        log.info("[NEWS] 뉴스 수집 — 종목 {}개, 신규 {}건", targets.size(), created);
        return created;
    }

    // ── 내부 ─────────────────────────────────────────────────

    private int save(Stock stock, List<NaverNewsItem> items) {
        if (items.isEmpty()) {
            return 0;
        }
        List<String> externalIds =
                items.stream().map(item -> externalId(item.originUrl())).toList();
        // 건별로 존재 여부를 물으면 종목당 수십 번 왕복한다. 한 번에 받아 메모리에서 거른다.
        Set<String> known =
                researchDocumentRepository
                        .findAllBySourceAndExternalIdIn(
                                ResearchDocument.Source.NEWS, externalIds)
                        .stream()
                        .map(ResearchDocument::getExternalId)
                        .collect(Collectors.toCollection(HashSet::new));

        List<ResearchDocument> fresh = new ArrayList<>();
        for (NaverNewsItem item : items) {
            if (item.originUrl().length() > MAX_URL_LENGTH) {
                continue;
            }
            String externalId = externalId(item.originUrl());
            if (!known.add(externalId)) {
                // 이미 있거나, 같은 응답 안에 두 번 나온 건이다. UQ 위반으로 회차가 깨지는 것을 막는다.
                continue;
            }
            fresh.add(ResearchDocument.collected(
                    stock,
                    ResearchDocument.Source.NEWS,
                    externalId,
                    truncate(item.title()),
                    item.originUrl(),
                    item.snippet(),
                    item.publishedAt()));
        }
        researchDocumentRepository.saveAll(fresh);
        return fresh.size();
    }

    /**
     * 종목명 검색은 무관 기사를 함께 데려온다 — "한화"로 검색하면 야구 기사가 섞인다. 제목에
     * 종목명이 들어간 건만 남기는 것이 가장 값싼 1차 방어다.
     *
     * <p>공백을 지우고 비교하는 이유는 매체마다 "삼성전자"와 "삼성 전자"를 오가서다.
     *
     * <p><b>발췌까지 보면 필터가 무의미해진다.</b> 2026-09-03 실측 — "삼성전자" 20건 중
     * 제목에 걸리는 것은 3건이지만, 발췌까지 포함하면 20건 전부가 통과한다. 검색어가 발췌에
     * 늘 강조되어 들어가기 때문이다. 그러면 부동산·통신사 기사까지 종목 카드에 뜬다.
     *
     * <p>ponytail: 제목만 보는 단순 규칙이라, 회사명을 쓰지 않고 "삼성"·"삼전" 같은 약칭으로만
     * 지칭한 기사는 놓친다. 정확도를 택했다 — 놓친 기사보다 엉뚱한 기사가 카드에 뜨는 쪽이
     * 나쁘다. 재현율이 문제가 되면 종목별 별칭 표를 두는 것이 다음 단계다.
     */
    static boolean mentions(String title, String stockName) {
        if (title == null || stockName == null || stockName.isBlank()) {
            return false;
        }
        return title.replace(" ", "").contains(stockName.replace(" ", ""));
    }

    /**
     * 원문 주소의 SHA-256(16진). 주소를 그대로 키로 쓰지 않는 이유는 컬럼 폭이다 —
     * {@code external_id} 는 100자인데 기사 주소는 그보다 긴 것이 흔하다. 해시는 64자로 고정이다.
     */
    static String externalId(String originUrl) {
        String normalized = originUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 JDK 표준이라 없을 수 없다. 검사 예외를 위로 흘려보내지 않는다.
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }

    private static String truncate(String title) {
        if (title == null || title.isBlank()) {
            return "(제목 없음)";
        }
        String trimmed = title.trim();
        return trimmed.length() <= MAX_TITLE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_TITLE_LENGTH);
    }
}
