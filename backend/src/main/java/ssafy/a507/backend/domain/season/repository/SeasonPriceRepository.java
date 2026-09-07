package ssafy.a507.backend.domain.season.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.season.entity.SeasonPrice;

public interface SeasonPriceRepository extends JpaRepository<SeasonPrice, Long> {

    /**
     * 진행일까지의 봉. 상한을 <b>쿼리에서</b> 거는 것이 요점이다 — 전부 읽어 화면에서
     * 자르면 응답에 실려 나가 개발자도구로 다음 날 종가가 다 보인다.
     *
     * <p>UQ(ticker_id, game_day) 를 그대로 타고, 게임일 오름차순이라 차트가 받은 순서대로
     * 그린다.
     */
    List<SeasonPrice> findByTicker_IdAndGameDayLessThanEqualOrderByGameDayAsc(
            Long tickerId, int uptoDay);
}
