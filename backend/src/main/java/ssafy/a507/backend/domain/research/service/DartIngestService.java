package ssafy.a507.backend.domain.research.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.a507.backend.domain.market.entity.Stock;
import ssafy.a507.backend.domain.market.repository.StockRepository;
import ssafy.a507.backend.domain.research.client.CorpCodeRow;
import ssafy.a507.backend.domain.research.client.DartClient;
import ssafy.a507.backend.domain.research.client.DartCompany;
import ssafy.a507.backend.domain.research.client.DartDisclosure;
import ssafy.a507.backend.domain.research.client.DartException;
import ssafy.a507.backend.domain.research.client.DartFinancialSnapshot;
import ssafy.a507.backend.domain.research.client.DartProperties;
import ssafy.a507.backend.domain.research.entity.CorpFinancial;
import ssafy.a507.backend.domain.research.entity.CorpProfile;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;
import ssafy.a507.backend.domain.research.repository.CorpFinancialRepository;
import ssafy.a507.backend.domain.research.repository.CorpProfileRepository;
import ssafy.a507.backend.domain.research.repository.ResearchDocumentRepository;

/**
 * DART 수집 (ANT-RESEARCH-01).
 *
 * <p>네 갈래이고 주기가 각각 다르다 — 고유번호 시드 · 기업개황 · 연간 재무 · 공시 목록.
 * "폴링"이라는 한 단어로 묶으면 안 되는 이유다: 공시 목록만 매일이고, 나머지는 월·분기다.
 *
 * <p><b>수집 회차 표({@code ingest_runs})를 쓰지 않는다.</b> 그 표는 {@code base_date} 가
 * 유니크라 하루 한 행이고, 일봉 수집이 이미 그 자리를 쓴다 — DART 가 끼어들면 두 배치가 같은
 * 행을 두고 부딪힌다({@code IngestRun} 이 {@code batch_runs} 를 피한 것과 같은 이유다).
 * 게다가 여기서는 필요가 없다: 접수번호 유니크와 {@code (종목, 연도, 분기)} 유니크가 재실행
 * 멱등을 이미 보장하므로, 실패한 대상은 다음 회차가 자연스럽게 다시 집는다.
 *
 * <p><b>회차 전체를 한 트랜잭션으로 묶지 않는다.</b> 종목 수백 개에 대한 HTTP 호출이 안에
 * 들어가면 DB 커넥션 하나를 수 분간 붙잡는다. 대신 종목 하나를 저장할 때마다 짧은 트랜잭션이
 * 돌게 두고(리포지토리 기본 동작), 한 종목의 실패가 나머지를 끌고 내려가지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartIngestService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** {@code research_documents.title} 컬럼 폭. 보고서명이 길면 잘라서라도 넣는다. */
    private static final int MAX_TITLE_LENGTH = 200;

    private final DartClient dartClient;
    private final DartProperties properties;
    private final StockRepository stockRepository;
    private final CorpProfileRepository corpProfileRepository;
    private final CorpFinancialRepository corpFinancialRepository;
    private final ResearchDocumentRepository researchDocumentRepository;

    /**
     * 고유번호 시드. <b>나머지 셋의 선행이다</b> — DART 의 다른 API 는 종목코드가 아니라
     * {@code corp_code} 를 받으므로, 이 매핑이 없으면 아무것도 부를 수 없다.
     *
     * <p>파일에는 12만 건이 있지만 우리가 남기는 것은 {@code stocks} 에 있는 종목뿐이다.
     * 나머지는 비상장이거나 우리 수집 범위 밖이라 프로필을 만들어 둘 이유가 없다.
     *
     * @return 새로 만들거나 고유번호를 갱신한 종목 수
     */
    public int seedCorpCodes() {
        if (skip("고유번호 시드")) {
            return 0;
        }

        Set<String> targets = targetStockCodes();
        if (targets.isEmpty()) {
            log.warn("[DART] stocks 가 비어 있어 고유번호 시드를 건너뛴다 — 일봉 수집이 먼저다");
            return 0;
        }

        List<CorpCodeRow> rows = dartClient.fetchListedCorpCodes();
        Map<String, CorpProfile> existing = existingProfiles(targets);

        List<CorpProfile> changed = new ArrayList<>();
        for (CorpCodeRow row : rows) {
            if (!targets.contains(row.stockCode())) {
                continue;
            }
            CorpProfile profile = existing.get(row.stockCode());
            if (profile == null) {
                changed.add(CorpProfile.of(row.stockCode(), row.corpCode(), row.corpName()));
            } else if (!row.corpCode().equals(profile.getCorpCode())) {
                // 합병·재상장으로 고유번호가 바뀌는 일이 있다. 바뀐 것만 다시 쓴다.
                changed.add(CorpProfile.of(row.stockCode(), row.corpCode(), row.corpName()));
            }
        }

        corpProfileRepository.saveAll(changed);
        log.info("[DART] 고유번호 시드 — 대상 {}종목 중 {}건 반영", targets.size(), changed.size());
        return changed.size();
    }

    /** 기업개황. 고유번호가 잡힌 종목만 돈다. */
    public int ingestProfiles() {
        if (skip("기업개황")) {
            return 0;
        }
        List<CorpProfile> profiles = corpProfileRepository.findAll();
        int updated = 0;
        for (CorpProfile profile : profiles) {
            try {
                DartCompany company = dartClient.fetchCompany(profile.getCorpCode());
                if (company == null) {
                    continue;
                }
                profile.update(
                        company.corpName(),
                        company.corpNameEng(),
                        company.ceoName(),
                        company.industryCode(),
                        company.address(),
                        company.homepageUrl(),
                        company.irUrl(),
                        company.establishedDate(),
                        company.accountMonth());
                corpProfileRepository.save(profile);
                updated++;
            } catch (DartException e) {
                if (e.isRateLimited()) {
                    log.warn("[DART] 기업개황 — 한도 초과로 회차를 접는다 ({}건 반영)", updated);
                    break;
                }
                log.warn("[DART] 기업개황 실패 {} — {}", profile.getStockCode(), e.getMessage());
            }
        }
        log.info("[DART] 기업개황 — {}/{}종목 반영", updated, profiles.size());
        return updated;
    }

    /**
     * 연간 재무. <b>한 종목당 호출 한 번이면 3개년이 채워진다</b> — 응답의 각 계정 행이
     * 당기·전기·전전기를 함께 담고 있어서다.
     *
     * @param year 기준 사업연도. 이 해와 앞의 두 해가 함께 저장된다
     */
    public int ingestAnnualFinancials(int year) {
        if (skip("연간 재무")) {
            return 0;
        }
        List<CorpProfile> profiles = corpProfileRepository.findAll();
        int saved = 0;
        for (CorpProfile profile : profiles) {
            try {
                List<DartFinancialSnapshot> snapshots =
                        dartClient.fetchAnnualFinancials(profile.getCorpCode(), year);
                for (DartFinancialSnapshot snapshot : snapshots) {
                    upsertFinancial(profile.getStockCode(), snapshot);
                    saved++;
                }
            } catch (DartException e) {
                if (e.isRateLimited()) {
                    log.warn("[DART] 연간 재무 — 한도 초과로 회차를 접는다 ({}행 반영)", saved);
                    break;
                }
                log.warn("[DART] 연간 재무 실패 {} — {}", profile.getStockCode(), e.getMessage());
            }
        }
        log.info("[DART] 연간 재무 {}년 기준 — {}행 반영", year, saved);
        return saved;
    }

    /**
     * 공시 목록. 구간을 겹쳐 훑어도 접수번호 유니크가 중복을 막는다 — 연휴나 실패 회차를
     * 메우려고 {@code lookbackDays} 만큼 되돌아본다.
     */
    public int ingestDisclosures(LocalDate from, LocalDate to) {
        if (skip("공시 목록")) {
            return 0;
        }
        List<CorpProfile> profiles = corpProfileRepository.findAll();
        int created = 0;
        for (CorpProfile profile : profiles) {
            try {
                created += saveDisclosures(profile, dartClient.fetchDisclosures(
                        profile.getCorpCode(), from, to));
            } catch (DartException e) {
                if (e.isRateLimited()) {
                    log.warn("[DART] 공시 목록 — 한도 초과로 회차를 접는다 ({}건 반영)", created);
                    break;
                }
                log.warn("[DART] 공시 목록 실패 {} — {}", profile.getStockCode(), e.getMessage());
            }
        }
        log.info("[DART] 공시 목록 {}~{} — 신규 {}건", from, to, created);
        return created;
    }

    /** 되돌아볼 구간을 설정에서 계산한다. 스케줄러가 날짜를 직접 만들지 않게 한다. */
    public LocalDate disclosureFrom(LocalDate today) {
        return today.minusDays(properties.lookbackDays());
    }

    /** 재무 기준 연도. 설정이 비면 "작년" — 올해 사업보고서는 아직 나오지 않았다. */
    public int financialYear(LocalDate today) {
        int configured = properties.financialYear();
        return configured > 0 ? configured : today.getYear() - 1;
    }

    // ── 내부 ─────────────────────────────────────────────────

    private int saveDisclosures(CorpProfile profile, List<DartDisclosure> disclosures) {
        if (disclosures.isEmpty()) {
            return 0;
        }
        List<String> receiptNos = disclosures.stream().map(DartDisclosure::receiptNo).toList();
        // 건별로 존재 여부를 물으면 종목당 수십 번 왕복한다. 한 번에 받아 메모리에서 거른다.
        Set<String> known =
                researchDocumentRepository
                        .findAllBySourceAndExternalIdIn(ResearchDocument.Source.DART, receiptNos)
                        .stream()
                        .map(ResearchDocument::getExternalId)
                        .collect(Collectors.toCollection(HashSet::new));

        List<ResearchDocument> fresh = new ArrayList<>();
        for (DartDisclosure disclosure : disclosures) {
            if (!known.add(disclosure.receiptNo())) {
                // 이미 있거나, 같은 응답 안에 두 번 나온 건이다. UQ 위반으로 배치가 깨지는 것을 막는다.
                continue;
            }
            LocalDate receivedOn = parseDate(disclosure.receiptDate());
            if (receivedOn == null) {
                continue;
            }
            // getReferenceById 는 쿼리를 날리지 않고 프록시만 만든다 — 삽입에 필요한 것은
            // FK 값뿐이라 종목을 실제로 읽어 올 이유가 없다.
            fresh.add(ResearchDocument.collected(
                    stockRepository.getReferenceById(profile.getStockCode()),
                    ResearchDocument.Source.DART,
                    disclosure.receiptNo(),
                    truncate(disclosure.reportName()),
                    disclosure.originUrl(),
                    receivedOn.atStartOfDay(KST).toInstant()));
        }
        researchDocumentRepository.saveAll(fresh);
        return fresh.size();
    }

    private void upsertFinancial(String stockCode, DartFinancialSnapshot snapshot) {
        CorpFinancial financial =
                corpFinancialRepository
                        .findByStockCodeAndFiscalYearAndQuarter(
                                stockCode, snapshot.year(), CorpFinancial.ANNUAL_QUARTER)
                        .orElseGet(() -> CorpFinancial.of(
                                stockCode, snapshot.year(), CorpFinancial.ANNUAL_QUARTER));
        financial.update(
                snapshot.fsDiv(),
                snapshot.currency(),
                snapshot.receiptNo(),
                snapshot.revenue(),
                snapshot.operatingProfit(),
                snapshot.netIncome(),
                snapshot.totalAssets(),
                snapshot.totalLiabilities(),
                snapshot.totalEquity());
        corpFinancialRepository.save(financial);
    }

    /**
     * 수집 대상 종목코드. {@code stocks} 에 있는 상장 종목만 본다 — 임대연님 수집이 KOSPI
     * 시가총액 상위 300 으로 좁혀 놨고, 그 범위를 여기서 다시 정의하면 두 배치가 어긋난다.
     */
    private Set<String> targetStockCodes() {
        return stockRepository.findAll().stream()
                .filter(Stock::isListed)
                .map(Stock::getCode)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Map<String, CorpProfile> existingProfiles(Set<String> stockCodes) {
        return corpProfileRepository.findAllByStockCodeIn(List.copyOf(stockCodes)).stream()
                .collect(Collectors.toMap(CorpProfile::getStockCode, Function.identity()));
    }

    private boolean skip(String job) {
        if (properties.isConfigured()) {
            return false;
        }
        log.info("[DART] DART_API_KEY 가 없어 {}를 건너뛴다", job);
        return true;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), YMD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String truncate(String title) {
        if (title == null) {
            return "(제목 없음)";
        }
        String trimmed = title.trim();
        return trimmed.length() <= MAX_TITLE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_TITLE_LENGTH);
    }
}
