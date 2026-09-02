package ssafy.a507.backend.domain.research.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.research.entity.CorpProfile;

public interface CorpProfileRepository extends JpaRepository<CorpProfile, String> {

    /** 수집 대상 목록. 고유번호가 있어야 나머지 DART 호출이 가능하므로 여기서 출발한다. */
    List<CorpProfile> findAllByStockCodeIn(List<String> stockCodes);
}
