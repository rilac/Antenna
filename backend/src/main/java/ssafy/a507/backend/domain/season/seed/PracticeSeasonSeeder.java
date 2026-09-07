package ssafy.a507.backend.domain.season.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.market.seed.SectorSeeder;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.repository.SeasonParticipantRepository;
import ssafy.a507.backend.domain.season.repository.SeasonRepository;
import ssafy.a507.backend.domain.season.service.SeasonCreateService;
import ssafy.a507.backend.domain.season.service.SeasonCreateService.SeasonSpec;

/**
 * 연습 시즌(주제) 셋을 기동 때 만들어 둔다.
 *
 * <p><b>왜 관리자 API 가 아니라 시더인가.</b> 연습 시즌은 참가자가 만드는 것이 아니라
 * 서비스가 늘 열어 두는 것이고, G-02a 연습하기 화면은 목록이 비면 그릴 것이 없다. 그런데
 * 관리자 권한 경로가 아직 없다({@code SecurityConfig} 에 role 규칙도, admin 컨트롤러도 없다) —
 * 그걸 기다리면 화면이 계속 빈 상태로 남는다. {@code SectorSeeder} 와 같은 자리·같은 규칙이다.
 *
 * <p><b>두 번 돌아도 안전하다.</b> (모드, 대표 업종, seed) 가 같은 시즌이 있으면 건너뛴다.
 * 재료가 없어도 건너뛴다 — 백필을 아직 돌리지 않은 팀원의 로컬을 기동부터 죽이지 않는다.
 *
 * <p><b>스펙에서 빠진 연습 시즌은 지운다.</b> 주제가 바뀌면 옛 시즌이 목록에 함께 남아
 * 화면이 여섯 장이 된다. 단 누구든 참가한 시즌은 남긴다 — 기록이 딸려 있다.
 *
 * <p><b>업종 시더 뒤에 돈다.</b> 종목의 업종 힌트를 {@code stocks.sector} 에서 복사하므로
 * 업종이 비어 있는 채로 돌면 힌트가 빈 시즌이 남는다.
 *
 * <p>관리자 생성 API 가 들어오면 이 파일의 {@link #SEASONS} 가 그 요청 본문의 예시가 되고,
 * 시더는 지운다.
 */
@Slf4j
@Component
@Order(SectorSeeder.ORDER + 10)
@RequiredArgsConstructor
public class PracticeSeasonSeeder implements ApplicationRunner {

    /** 전원 공통 출발선. 3,000만원 — 1주에 수십만원인 종목도 몇 주는 잡을 수 있는 금액이다. */
    private static final BigDecimal INITIAL_CASH = new BigDecimal("30000000");

    /**
     * 시즌당 종목 수 상한. 그 구간 첫날 시가총액 상위 200 — 지수(KOSPI 200)와 같은 규모다.
     * 주제 업종 밖 종목까지 같은 장에서 어떻게 움직였는지 보라고 전부 담는다. 진행 화면이
     * 종목코드·종목명 검색과 업종 필터를 붙이는 전제다.
     */
    private static final int TICKER_COUNT = 200;

    /**
     * 만들어 둘 연습 시즌. 구간은 로컬 일봉으로 성격을 확인한 것들이다(2026-09-07).
     *
     * <p>길이는 20~60게임일로 짧게 둔다 — 참가자가 하루씩 직접 넘기므로 120번을 누르게 하면
     * 끝까지 가는 사람이 없다. 대신 앞에 워밍업 봉 30개가 붙어 추세는 충분히 보인다.
     *
     * <p>{@code title}·{@code note} 에 연도·사건 고유명사를 넣지 않는다. 종목은 실명이라
     * 마음먹으면 시기를 알아낼 수는 있지만, 화면이 먼저 말해 주지는 않는다.
     *
     * <p>기준일은 요청값이다. 그날이 휴일이면 시즌은 그다음 영업일부터 시작한다.
     */
    private static final List<SeasonSpec> SEASONS = List.of(
            new SeasonSpec(
                    Season.Mode.PRACTICE,
                    "급락과 반등",
                    "시장 전체가 짧은 기간에 크게 내렸다가 되돌아온 구간. 공포 속에서 파는 손과 저점을 잡는 손을 연습한다.",
                    "운송장비·부품",
                    LocalDate.of(2020, 2, 17),
                    TICKER_COUNT,
                    44,
                    INITIAL_CASH,
                    2001L),
            new SeasonSpec(
                    Season.Mode.PRACTICE,
                    "반도체 실적 구간",
                    "반도체 실적 기대가 시장을 끌어올린 구간. 실적 랠리에 올라타는 시점과 되돌림에서 이익을 지키는 법을 연습한다.",
                    "전기·전자",
                    LocalDate.of(2025, 10, 1),
                    TICKER_COUNT,
                    38,
                    INITIAL_CASH,
                    2002L),
            new SeasonSpec(
                    Season.Mode.PRACTICE,
                    "전기차 섹터 변동성",
                    "전기차·배터리 테마가 급등한 구간. 테마 추격 매수의 진입·청산 타이밍을 연습한다.",
                    "화학",
                    LocalDate.of(2023, 1, 16),
                    TICKER_COUNT,
                    62,
                    INITIAL_CASH,
                    2003L));

    private final SeasonRepository seasonRepository;
    private final SeasonParticipantRepository participantRepository;
    private final SeasonCreateService createService;

    @Override
    public void run(ApplicationArguments args) {
        removeStale();
        for (SeasonSpec spec : SEASONS) {
            if (seasonRepository.existsByModeAndThemeAndSeed(
                    spec.mode(), spec.theme(), spec.seed())) {
                continue;
            }
            try {
                createService.create(spec);
            } catch (IllegalStateException e) {
                // 재료 부족은 오류가 아니다. 백필이 끝난 뒤 다음 기동이 다시 시도한다.
                log.info("연습 시즌 \"{}\" 을 건너뛴다 — {}", spec.title(), e.getMessage());
            }
        }
    }

    /** 스펙 목록에 없는 연습 시즌을 지운다. 참가자가 한 명이라도 있으면 남긴다. */
    private void removeStale() {
        Set<String> keep = SEASONS.stream().map(s -> key(s.theme(), s.seed())).collect(Collectors.toSet());
        for (Season season : seasonRepository.findByMode(Season.Mode.PRACTICE)) {
            if (keep.contains(key(season.getTheme(), season.getSeed()))) {
                continue;
            }
            if (participantRepository.existsBySeason_Id(season.getId())) {
                log.info("옛 연습 시즌 \"{}\"(id={}) 은 참가자가 있어 남긴다", season.getTitle(), season.getId());
                continue;
            }
            createService.delete(season.getId());
            log.info("옛 연습 시즌 \"{}\"(id={}) 을 지웠다 — 스펙에 없다", season.getTitle(), season.getId());
        }
    }

    private static String key(String theme, Long seed) {
        return theme + "#" + seed;
    }
}
