package ssafy.a507.backend.domain.research.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.research.entity.ResearchDocument;

public interface ResearchDocumentRepository extends JpaRepository<ResearchDocument, Long> {}
