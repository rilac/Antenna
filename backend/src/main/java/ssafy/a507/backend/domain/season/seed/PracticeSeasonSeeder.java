package ssafy.a507.backend.domain.season.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ssafy.a507.backend.domain.market.seed.SectorSeeder;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.repository.SeasonRepository;
import ssafy.a507.backend.domain.season.service.SeasonCreateService;
import ssafy.a507.backend.domain.season.service.SeasonCreateService.SeasonSpec;

/**
 * 연습 시즌 셋을 기동 때 만들어 둔다.
 *
 * <p><b>왜 관리자 API 가 아니라 시더인가.</b> 연습 시즌은 참가자가 만드는 것이 아니라
 * 서비스가 늘 열어 두는 것이고, G-02a 연습하기 화면은 목록이 비면 그릴 것이 없다. 그런데
 * 관리자 권한 경로가 아직 없다({@code SecurityConfig} 에 role 규칙도, admin 컨트롤러도 없다) —
 * 그걸 기다리면 화면이 계속 빈 상태로 남는다. {@code SectorSeeder} 와 같은 자리·같은 규칙이다.
 *
 * <p><b>두 번 돌아도 안전하다.</b> (모드, 섹터, seed) 가 같은 시즌이 있으면 건너뛴다.
 * 재료가 없어도 건너뛴다 — 백필을 아직 돌리지 않은 팀원의 로컬을 기동부터 죽이지 않는다.
 *
 * <p><b>업종 시더 뒤에 돈다.</b> 종목을 {@code stocks.sector} 로 뽑으므로 업종이 비어 있는
 * 채로 돌면 후보가 0 이고, 그 기동에서는 연습하기 화면이 빈 채로 남는다.
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
     * 60게임일. 대회 기본값(120)의 절반이다 — 연습은 참가자가 직접 하루씩 넘기므로
     * 120번을 누르게 하면 끝까지 가는 사람이 없다.
     */
    private static final int LENGTH_DAYS = 60;

    /** 시즌당 종목 수. 진행 화면이 한 눈에 담을 수 있는 수다. */
    private static final int TICKER_COUNT = 5;

    /**
     * 만들어 둘 연습 시즌.
     *
     * <p>섹터는 수집 범위(KOSPI 300) 안에서 종목이 넉넉한 상위 분류를 골랐다. 기준일은 서로
     * 겹치지 않게 떨어뜨렸다 — 세 시즌이 같은 장을 보고 있으면 연습을 세 번 할 이유가 없다.
     *
     * <p>기준일은 요청값이다. 그날이 휴일이면 시즌은 그다음 영업일부터 시작한다.
     *
     * <p>{@code title} 은 업종만 말한다. 어느 방향으로 움직인 구간인지는 실제로 확인한 뒤에
     * 붙일 문구이고, 확인하지 않은 성격을 적으면 참가자가 틀린 힌트를 믿는다.
     */
    private static final List<SeasonSpec> SEASONS = List.of(
            new SeasonSpec(
                    Season.Mode.PRACTICE,
                    "전기전자 업종",
                    "반도체와 전자부품이 섞인 업종이다. 60게임일 동안 5개 종목을 굴려 본다.",
                    "전기·전자",
                    LocalDate.of(2021, 3, 1),
                    TICKER_COUNT,
                    LENGTH_DAYS,
                    INITIAL_CASH,
                    1001L),
            new SeasonSpec(
                    Season.Mode.PRACTICE,
                    "화학 업종",
                    "석유화학·소재·생활용품이 섞인 업종이다. 60게임일 동안 5개 종목을 굴려 본다.",
                    "화학",
                    LocalDate.of(2022, 6, 1),
                    TICKER_COUNT,
                    LENGTH_DAYS,
                    INITIAL_CASH,
                    1002L),
            new SeasonSpec(
                    Season.Mode.PRACTICE,
                    "운송장비 업종",
                    "자동차·조선 같은 운송장비 회사가 모인 업종이다. 60게임일 동안 5개 종목을 굴려 본다.",
                    "운송장비·부품",
                    LocalDate.of(2023, 9, 1),
                    TICKER_COUNT,
                    LENGTH_DAYS,
                    INITIAL_CASH,
                    1003L));

    private final SeasonRepository seasonRepository;
    private final SeasonCreateService createService;

    @Override
    public void run(ApplicationArguments args) {
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
}
