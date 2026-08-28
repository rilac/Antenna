package ssafy.a507.backend.domain.monetize.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.monetize.entity.Certificate;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {}
