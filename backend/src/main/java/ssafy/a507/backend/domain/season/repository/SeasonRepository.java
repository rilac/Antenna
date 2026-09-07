package ssafy.a507.backend.domain.season.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.season.entity.Season;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    /**
     * 같은 시즌이 이미 있는가. 시더가 기동마다 다시 돌아도 시즌이 겹쳐 쌓이지 않게 막는다.
     *
     * <p>기준일로 묻지 않는다 — 저장된 {@code base_date} 는 요청한 날이 아니라 그날 이후 첫
     * 영업일이라(3월 1일을 넣으면 3월 2일이 남는다) 요청값과는 맞지 않는다.
     * (모드, 섹터, seed) 가 같으면 같은 종목·같은 구간이 뽑히므로 같은 시즌이다.
     */
    boolean existsByModeAndThemeAndSeed(Season.Mode mode, String theme, Long seed);

    /**
     * 볼 수 있는 모드의 시즌 전부. 모드·상태 필터는 호출부가 이 결과에서 걸러 낸다 —
     * 동시에 열려 있는 시즌이 열 개 남짓이라 조건마다 쿼리를 갈아 낄 이유가 없다.
     *
     * <p>모드를 <b>쿼리에서</b> 거르는 것이 요점이다. DEMO 는 관리자 전용인데 응답에 실어
     * 보내고 화면에서 감추면 개발자도구로 다 보인다.
     */
    List<Season> findByModeInOrderByIdAsc(Collection<Season.Mode> modes);

    /** 한 모드의 시즌 전부 — 시더가 스펙에서 빠진 옛 연습 시즌을 찾을 때. */
    List<Season> findByMode(Season.Mode mode);
}
