package ssafy.a507.backend.domain.chain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.chain.entity.Operation;

public interface OperationRepository extends JpaRepository<Operation, String> {

    /** 인덱서 ②(ANT-CHAIN-11)가 이벤트의 tx 로 작업을 찾는다. 릴레이어가 전송 직후 채운 값이라 응답 시점엔 있다. */
    Optional<Operation> findByTxHash(String txHash);

    /** 전송 주체가 tx 를 못 보낸 채 남은 PENDING — "전송~markSent 사이 크래시" 창. 인덱서 ② 가 SEND_LOST 로 닫는다. */
    List<Operation> findByStatusAndTxHashIsNullAndCreatedAtBefore(Operation.Status status, Instant before);

    /** tx 는 보냈는데 이벤트가 아직 없는 PENDING. 인덱서 ② 가 receipt 로 revert·유실을 가른다. */
    List<Operation> findByStatusAndTxHashIsNotNullAndCreatedAtBefore(Operation.Status status, Instant before);
}
