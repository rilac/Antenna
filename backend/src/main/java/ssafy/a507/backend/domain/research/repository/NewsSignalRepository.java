package ssafy.a507.backend.domain.research.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.research.entity.NewsSignal;

public interface NewsSignalRepository extends JpaRepository<NewsSignal, Long> {

    /** 재료 선정에서 후보 문서들의 판정을 한 번에 읽는다 — 건별 조회는 왕복이 너무 잦다. */
    List<NewsSignal> findByDocument_IdIn(Collection<Long> documentIds);

    Optional<NewsSignal> findByDocument_Id(Long documentId);
}
