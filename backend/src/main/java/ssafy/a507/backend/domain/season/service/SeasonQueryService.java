package ssafy.a507.backend.domain.season.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.season.dto.MySeasonItemResponse;
import ssafy.a507.backend.domain.season.dto.MySeasonListResponse;
import ssafy.a507.backend.domain.season.dto.MySeasonStatus;
import ssafy.a507.backend.domain.season.dto.SeasonDetailResponse;
import ssafy.a507.backend.domain.season.dto.SeasonListItemResponse;
import ssafy.a507.backend.domain.season.dto.SeasonListResponse;
import ssafy.a507.backend.domain.season.dto.SeasonPriceListResponse;
import ssafy.a507.backend.domain.season.dto.SeasonPricePoint;
import ssafy.a507.backend.domain.season.dto.SeasonTickerItemResponse;
import ssafy.a507.backend.domain.season.dto.SeasonTickerListResponse;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.entity.SeasonParticipant;
import ssafy.a507.backend.domain.season.entity.SeasonTicker;
import ssafy.a507.backend.domain.season.repository.SeasonParticipantRepository;
import ssafy.a507.backend.domain.season.repository.SeasonPriceRepository;
import ssafy.a507.backend.domain.season.repository.SeasonRepository;
import ssafy.a507.backend.domain.season.repository.SeasonTickerCount;
import ssafy.a507.backend.domain.season.repository.SeasonTickerRepository;

/**
 * 시즌 조회 — 목록·상세·내 참가. 읽기만 한다.
 *
 * <p><b>이 서비스가 지키는 것은 하나다: 시기를 내보내지 않는다.</b> 응답 DTO 에
 * {@code baseDate} 자리가 아예 없어 실수로 담을 수 없다. 실제 날짜·연도·사건 고유명사는
 * 어떤 시즌 응답에도 나가지 않는다(API 명세 v0.24 · ERD v0.6).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonQueryService {

    private final SeasonRepository seasonRepository;
    private final SeasonTickerRepository seasonTickerRepository;
    private final SeasonPriceRepository seasonPriceRepository;
    private final SeasonParticipantRepository participantRepository;
    private final UserRepository userRepository;

    /** 일반 사용자가 보는 모드. 시연은 빠진다 — 관리자만 여는 화면이다(설계서 §3 G). */
    private static final EnumSet<Season.Mode> PUBLIC_MODES =
            EnumSet.of(Season.Mode.PRACTICE, Season.Mode.COMPETITION);

    public SeasonListResponse list(Long userId, Season.Mode mode, Season.Status status) {
        Collection<Season.Mode> visible = visibleModes(userId);
        if (mode != null && !visible.contains(mode)) {
            // 볼 수 없는 모드를 물었다. 빈 목록이다 — 거부하면 그 모드가 있다는 사실이 샌다.
            return new SeasonListResponse(List.of());
        }
        List<Season> seasons = seasonRepository.findByModeInOrderByIdAsc(visible).stream()
                .filter(s -> mode == null || s.getMode() == mode)
                .filter(s -> status == null || s.getStatus() == status)
                .toList();

        Map<Long, Integer> counts = tickerCounts(seasons);
        return new SeasonListResponse(seasons.stream()
                .map(s -> new SeasonListItemResponse(
                        s.getId(),
                        s.getMode(),
                        s.getStatus(),
                        s.getTitle(),
                        s.getNote(),
                        s.getTheme(),
                        sectorOf(s),
                        s.getLengthDays(),
                        s.getInitialCash(),
                        counts.getOrDefault(s.getId(), 0),
                        entryFeeOf(s)))
                .toList());
    }

    public SeasonDetailResponse detail(Long userId, Long seasonId) {
        Season season = visibleSeason(userId, seasonId);

        // 이어하기 대상은 진행 중 회차뿐이다. 끝났거나 버린 회차만 있으면 "참가" 로 새 회차를 연다.
        Optional<SeasonParticipant> mine =
                participantRepository.findFirstBySeason_IdAndUser_IdOrderByAttemptNoDesc(
                        seasonId, userId)
                        .filter(SeasonParticipant::isOngoing);

        return new SeasonDetailResponse(
                season.getId(),
                season.getMode(),
                season.getStatus(),
                season.getTitle(),
                season.getNote(),
                season.getTheme(),
                sectorOf(season),
                season.getLengthDays(),
                season.getInitialCash(),
                (int) seasonTickerRepository.countBySeason_Id(seasonId),
                entryFeeOf(season),
                season.getOpensAt(),
                season.getClosesAt(),
                season.getDayIntervalMinutes(),
                mine.isPresent(),
                mine.map(SeasonParticipant::getCurrentDay).orElse(null));
    }

    /**
     * 내 참가 목록. 진행 중인 쪽이 이어하기 진입점이다.
     *
     * <p>끝났는지는 시즌 상태가 아니라 <b>내 회차 상태</b>로 가른다 — 연습은 사람마다 진행이
     * 다르므로 같은 시즌이 누구에게는 진행 중이고 누구에게는 끝난 것이다. 초기화로 버린
     * 회차(ABANDONED)는 목록에 없다.
     */
    public MySeasonListResponse mine(Long userId, MySeasonStatus status) {
        return new MySeasonListResponse(
                participantRepository.findByUser_IdOrderByIdDesc(userId).stream()
                        .filter(p -> p.getStatus() != SeasonParticipant.Status.ABANDONED)
                        .filter(p -> status == null || status == statusOf(p))
                        .map(p -> new MySeasonItemResponse(
                                p.getSeason().getId(),
                                p.getSeason().getMode(),
                                p.getCurrentDay(),
                                p.getSeason().getLengthDays(),
                                progressOf(p.getCurrentDay(), p.getSeason().getLengthDays())))
                        .toList());
    }

    /**
     * 시즌 종목 목록. 가명과 섹터 힌트만 나간다 — 정답 원본 종목은 CLOSED 전까지 금지다.
     *
     * <p>진행일과 무관하다. 어떤 종목이 있는지는 첫날부터 다 보여야 판을 짤 수 있다.
     */
    public SeasonTickerListResponse tickers(Long userId, Long seasonId) {
        Season season = visibleSeason(userId, seasonId);
        return new SeasonTickerListResponse(
                seasonTickerRepository.findBySeason_IdOrderByDisplayNameAsc(season.getId()).stream()
                        .map(t -> new SeasonTickerItemResponse(
                                t.getId(), t.getDisplayName(), t.getSector()))
                        .toList());
    }

    /**
     * 시즌 가격(OHLCV). <b>진행일을 넘는 봉은 내리지 않는다.</b>
     *
     * <p>화면에서 자르는 것으로는 부족하다 — 응답에 실려 나가면 개발자도구로 다음 날
     * 종가가 다 보인다. 그래서 상한을 쿼리에 건다.
     *
     * @param uptoDay 이 게임일까지 · 생략하면 진행일까지. 진행일보다 크게 넣어도 진행일에서
     *     잘린다(요청으로 상한을 넘길 수 없다)
     */
    public SeasonPriceListResponse prices(
            Long userId, Long seasonId, Long tickerId, Integer uptoDay) {
        Season season = visibleSeason(userId, seasonId);
        SeasonTicker ticker = seasonTickerRepository
                .findByIdAndSeason_Id(tickerId, season.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_TICKER_NOT_FOUND));

        int limit = visibleDay(season, userId);
        int upto = uptoDay == null ? limit : Math.min(uptoDay, limit);

        return new SeasonPriceListResponse(
                seasonPriceRepository
                        .findByTicker_IdAndGameDayLessThanEqualOrderByGameDayAsc(
                                ticker.getId(), upto)
                        .stream()
                        .map(p -> new SeasonPricePoint(
                                p.getGameDay(),
                                p.getOpen(),
                                p.getHigh(),
                                p.getLow(),
                                p.getClose(),
                                p.getVolume()))
                        .toList());
    }

    /**
     * 어디까지 볼 수 있는가.
     *
     * <p>대회는 <b>공용 진행일</b>이다 — 전원이 같은 날을 보고 있어야 순위가 뜻을 갖는다.
     * 연습·시연은 <b>내 회차의 진행일</b>이다. 사람마다 진행일이 달라 같은 시즌에서도
     * 보이는 봉 수가 다르다.
     *
     * <p>참가하지 않았으면 0 이다 — 참가 전에는 시즌 가격을 볼 수 없다. 성격·섹터·기간만
     * 보고 고르는 것이 설계다(설계서 §4 G-03).
     */
    private int visibleDay(Season season, Long userId) {
        if (season.getMode() == Season.Mode.COMPETITION) {
            return season.getCurrentDay();
        }
        return participantRepository
                .findFirstBySeason_IdAndUser_IdOrderByAttemptNoDesc(season.getId(), userId)
                .map(SeasonParticipant::getCurrentDay)
                .orElse(0);
    }

    /** 볼 수 있는 시즌이거나 404. 시연 시즌을 일반 사용자가 부른 것도 404 다. */
    private Season visibleSeason(Long userId, Long seasonId) {
        return seasonRepository
                .findById(seasonId)
                .filter(s -> visibleModes(userId).contains(s.getMode()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));
    }

    /** 시연은 관리자에게만 보인다. 권한 규칙이 없어(SecurityConfig) users.role 을 직접 읽는다. */
    private Collection<Season.Mode> visibleModes(Long userId) {
        boolean admin = userRepository
                .findById(userId)
                .map(u -> u.getRole() == User.Role.ADMIN)
                .orElse(false);
        return admin ? EnumSet.allOf(Season.Mode.class) : PUBLIC_MODES;
    }

    /** 시즌별 종목 수를 한 번에 센다. 시즌이 없으면 부르지 않는다 — 빈 in 절은 보내지 않는다. */
    private Map<Long, Integer> tickerCounts(List<Season> seasons) {
        if (seasons.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = seasons.stream().map(Season::getId).toList();
        Map<Long, Integer> counts = new HashMap<>();
        for (SeasonTickerCount row : seasonTickerRepository.countBySeasonIdIn(ids)) {
            counts.put(row.seasonId(), (int) row.count());
        }
        return counts;
    }

    /**
     * 카드에 보이는 대표 업종. 시즌은 전 업종의 대형주를 담으므로(v0.8) 종목별 업종은
     * {@code season_tickers.sector} 에 있고, 여기 {@code theme} 은 주제가 말하는 업종 하나다.
     */
    private String sectorOf(Season season) {
        return season.getTheme();
    }

    /**
     * 참가비. 아직 어느 표에도 없어 항상 null 이다 — 대회 금액표(D4)가 확정되면
     * {@code seasons} 에 컬럼이 생기고 여기가 그 값을 읽는다. 연습·시연은 그때도 null 이다.
     */
    private Integer entryFeeOf(Season season) {
        return null;
    }

    /** 끝남은 진행일이 아니라 회차 상태(DONE)다 — 마지막 게임일에도 종료 전이면 진행 중이다(v0.35). */
    private static MySeasonStatus statusOf(SeasonParticipant participant) {
        return participant.getStatus() == SeasonParticipant.Status.DONE
                ? MySeasonStatus.DONE
                : MySeasonStatus.ONGOING;
    }

    /** 진행률(%). 총 게임일이 0 인 시즌은 만들 수 없지만 0 으로 나누지는 않는다. */
    private static int progressOf(int currentDay, int lengthDays) {
        if (lengthDays <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, Math.round(currentDay * 100f / lengthDays)));
    }
}
