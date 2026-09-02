package ssafy.a507.backend.domain.chain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.chain.dto.OperationResponse;
import ssafy.a507.backend.domain.chain.entity.Operation;
import ssafy.a507.backend.domain.chain.repository.OperationRepository;

/**
 * 202 작업의 접수와 조회 (ANT-COMMUNITY-07).
 *
 * <p>{@link #accept} 는 202 를 반환하는 도메인 서비스가 부르고, {@link #find} 는 폴링이 부른다.
 * PENDING → SUCCEEDED/FAILED 전이는 여기 없다 — 체인 인덱서가 {@code chain_events} 기입과
 * 같은 트랜잭션에서 엔티티의 {@code markSucceeded}/{@code markFailed} 를 부른다.
 *
 * <p>ponytail: 명세의 "종료 상태 24h 보관 후 정리" 배치는 만들지 않았다. 정리하지 않아도
 * 조회는 정확하고(설정 종료 상태 그대로 응답), 행이 쌓이는 속도가 하루 수십 건 수준이다.
 * 실제로 커지면 {@code settled_at} 인덱스를 타는 삭제 배치 하나를 붙인다.
 */
@Service
@RequiredArgsConstructor
public class OperationService {

    private final OperationRepository operationRepository;

    /** 202 접수. 호출부의 트랜잭션에 얹혀 리소스 생성과 같은 커밋 단위로 묶인다. */
    @Transactional
    public Operation accept(
            User user, Operation.Kind kind, Operation.ResourceType resourceType, Long resourceId) {
        return operationRepository.save(Operation.accept(user, kind, resourceType, resourceId));
    }

    /**
     * 폴링 조회. 남의 작업은 403 이다 — 404 로 숨기는 편이 정보 노출은 적지만 명세가 403 으로
     * 못박았고, id 가 UUID 라 애초에 남의 것을 지목하려면 값을 알고 있어야 한다.
     */
    @Transactional(readOnly = true)
    public OperationResponse find(Long userId, String operationId) {
        Operation operation =
                operationRepository
                        .findById(operationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_NOT_FOUND));

        if (!operation.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.OPERATION_FORBIDDEN);
        }
        return OperationResponse.from(operation);
    }
}
