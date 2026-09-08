package ssafy.a507.backend.domain.season.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.season.entity.Season;
import ssafy.a507.backend.domain.season.entity.SeasonParticipant;
import ssafy.a507.backend.domain.season.repository.SeasonParticipantRepository;
import ssafy.a507.backend.domain.season.repository.SeasonPositionRepository;
import ssafy.a507.backend.domain.season.repository.SeasonRepository;

/**
 * 시즌 참가 — {@code POST /seasons/{id}/join}(ANT-SEASON-03).
 *
 * <p>연습·시연만 받는다. 대회는 참가비 소각 서명이 붙어 아직 없다(501).
 *
 * <p><b>회차.</b> 연습은 여러 번 다시 할 수 있다 — 마지막 회차가 끝났으면(DONE)
 * {@code attempt_no + 1} 로 새 회차, 진행 중(ONGOING)이면 409. "이어하기" 는 참가가 아니라 그
 * 회차로 들어가는 것이다. {@code restart} 면 진행 중 회차를 ABANDONED 로 버리고 새 회차다 —
 * 보유는 지우고 체결 내역은 남긴다(append-only).
 *
 * <p><b>예수금은 로컬 원장이다.</b> 금융망 계좌 개설·입금(ANT-SEASON-06)은 붙이지 않는다.
 * {@code season_participants.cash} 가 유일한 잔액이다.
 */
@Service
@RequiredArgsConstructor
public class SeasonJoinService {

    private final SeasonRepository seasonRepository;
    private final SeasonParticipantRepository participantRepository;
    private final SeasonPositionRepository positionRepository;
    private final UserRepository userRepository;

    /** 참가 결과. 화면은 진행일만 쓰지만 회차 번호도 함께 준다 — 기록이 회차에 귀속된다. */
    public record Joined(Long participantId, int attemptNo, int currentDay) {}

    @Transactional
    public Joined join(Long userId, Long seasonId, boolean restart) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Season season = seasonRepository.findById(seasonId)
                .filter(s -> s.getMode() != Season.Mode.DEMO || user.getRole() == User.Role.ADMIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));

        if (season.getMode() == Season.Mode.COMPETITION) {
            throw new BusinessException(ErrorCode.SEASON_JOIN_NOT_SUPPORTED);
        }
        if (season.getStatus() != Season.Status.RUNNING) {
            throw new BusinessException(ErrorCode.SEASON_NOT_RUNNING);
        }

        Optional<SeasonParticipant> last =
                participantRepository.findFirstBySeason_IdAndUser_IdOrderByAttemptNoDesc(seasonId, userId);
        if (last.isPresent() && last.get().isOngoing()) {
            if (!restart) {
                throw new BusinessException(ErrorCode.SEASON_ALREADY_JOINED);
            }
            last.get().abandon();
            positionRepository.deleteByParticipant_Id(last.get().getId());
        }
        short attemptNo = (short) (last.map(SeasonParticipant::getAttemptNo).orElse((short) 0) + 1);

        SeasonParticipant saved =
                participantRepository.save(SeasonParticipant.join(season, user, attemptNo));
        return new Joined(saved.getId(), saved.getAttemptNo(), saved.getCurrentDay());
    }
}
