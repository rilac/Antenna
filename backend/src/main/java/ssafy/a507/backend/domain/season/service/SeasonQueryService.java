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
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.entity.SeasonParticipant;
import ssafy.a507.backend.domain.season.repository.SeasonParticipantRepository;
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
        Season season = seasonRepository
                .findById(seasonId)
                .filter(s -> visibleModes(userId).contains(s.getMode()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));

        Optional<SeasonParticipant> mine =
                participantRepository.findFirstBySeason_IdAndUser_IdOrderByAttemptNoDesc(
                        seasonId, userId);

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
     * <p>끝났는지는 시즌 상태가 아니라 <b>내 진행일</b>로 가른다 — 연습은 사람마다 진행일이
     * 다르므로 같은 시즌이 누구에게는 진행 중이고 누구에게는 끝난 것이다.
     */
    public MySeasonListResponse mine(Long userId, MySeasonStatus status) {
        return new MySeasonListResponse(
                participantRepository.findByUser_IdOrderByIdDesc(userId).stream()
                        .filter(p -> status == null || status == statusOf(p))
                        .map(p -> new MySeasonItemResponse(
                                p.getSeason().getId(),
                                p.getSeason().getMode(),
                                p.getCurrentDay(),
                                p.getSeason().getLengthDays(),
                                progressOf(p.getCurrentDay(), p.getSeason().getLengthDays())))
                        .toList());
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
     * 참가자에게 보이는 섹터 힌트. 시즌은 한 섹터에서 종목을 뽑으므로 {@code theme} 이 곧
     * 그 섹터다 — 섹터를 섞는 시즌이 생기면 그때 {@code season_tickers.sector} 를 모아 만든다.
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

    private static MySeasonStatus statusOf(SeasonParticipant participant) {
        return participant.getCurrentDay() >= participant.getSeason().getLengthDays()
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
