package ssafy.a507.backend.domain.monetize.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.monetize.entity.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {}
