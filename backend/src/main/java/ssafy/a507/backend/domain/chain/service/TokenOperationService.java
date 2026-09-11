package ssafy.a507.backend.domain.chain.service;

import java.math.BigInteger;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;
import ssafy.a507.backend.domain.chain.entity.Operation;
import ssafy.a507.backend.domain.chain.relay.TokenReason;
import ssafy.a507.backend.domain.chain.relay.TokenRelayer;
import ssafy.a507.backend.domain.chain.relay.TokenRevertException;
import ssafy.a507.backend.domain.chain.repository.OperationRepository;

/**
 * 202 로 접수한 작업의 토큰 tx 를 보낸다 (ANT-CHAIN-10). PRED-01 슬롯 초과 소각 · TOKEN-01 구독 · TOKEN-07 mint 가 부른다.
 *
 * <p><b>호출자 계약 — 트랜잭션을 손으로 나눈다</b> ({@code SeasonPlayService.finish} 가 LLM 호출을 밖에 두는 것과 같은 모양):
 * <pre>
 * public XxxResponse create(userId, req) {                        // @Transactional 없음
 *     tokenOperationService.requireEnabled();                      // 꺼져 있으면 503. 아직 아무것도 안 썼다
 *     Accepted a = tx.execute(s -> accept(userId, req));           // 검사 · 서명(nonce 소비) · 리소스 PENDING · Operation.accept → 커밋
 *     tokenOperationService.dispatchBurn(a.opId(), price, reason);  // 트랜잭션 밖. 실패면 Operation FAILED + 예외
 *     return ...;                                                  // 202
 * }
 * </pre>
 *
 * <p>왜 DB 커밋이 먼저인가: 체인에 나갔는데 DB 커밋이 실패하면 <b>소각은 됐는데 예측은 없다</b> — 되돌릴 수단이 없다.
 * 반대(DB 에 PENDING 인데 체인엔 안 감)는 여기서 FAILED 로 닫는다. 남는 창은 "전송 ~ markSent 사이 크래시" 뿐이고
 * 앵커 배치도 같은 창을 감수한다. 그 행은 인덱서 ②(ANT-CHAIN-11)가 {@code tx_hash IS NULL} 정리로 받는다.
 *
 * <p>그래서 {@code dispatch*} 는 <b>활성 트랜잭션 안에서 부르면 {@link IllegalStateException}</b> 이다. 실수로
 * {@code @Transactional} 을 붙이면 위 실패 모양이 되는데, 그걸 개발 중에 바로 터뜨린다.
 *
 * <p>실패를 202 로 감추지 않는다. 시뮬레이션에서 잔액 부족이면 409, RPC 장애면 503 — Operation 은 FAILED 로 닫고
 * 예외를 던져 {@code GlobalExceptionHandler} 가 상태코드로 바꾸게 한다. 즉시 재시도는 없다(요청 스레드다). 재시도는
 * 사용자가 다시 누르는 것이고 그건 새 Operation 이다.
 *
 * <p>지갑 주소는 호출자가 넘기지 않는다. Operation 의 사용자(구독은 {@code creatorUserId} 로 예측가)에게서 읽는다 —
 * 주소 문자열이 호출자 코드에 돌아다니지 않고 지갑 미연동 검사가 한 곳이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenOperationService {

    private final TokenRelayer relayer;
    private final OperationRepository operations;
    private final UserRepository users;
    /** 접수/전송 경계를 이 클래스 안에서도 손으로 나눈다 — markSent·markFailed 는 각각 짧은 트랜잭션이다. */
    private final TransactionTemplate tx;

    /** 릴레이어가 꺼져 있으면(RPC·키·토큰 주소) 503. 호출자는 접수 트랜잭션 <b>전에</b> 부른다 — 아무것도 안 쓴 채 거절한다. */
    public void requireEnabled() {
        if (!relayer.isEnabled()) {
            throw new BusinessException(ErrorCode.CHAIN_UNAVAILABLE);
        }
    }

    /** Operation 의 사용자 지갑으로 {@code mint(to, amount, reason)}. @return tx 해시 */
    public String dispatchMint(String operationId, BigInteger amount, TokenReason reason) {
        return dispatch(operationId, "mint", w -> relayer.mint(w.self(), amount, reason), null);
    }

    /** Operation 의 사용자 지갑에서 {@code burn(from, amount, reason)}. @return tx 해시 */
    public String dispatchBurn(String operationId, BigInteger amount, TokenReason reason) {
        return dispatch(operationId, "burn", w -> relayer.burn(w.self(), amount, reason), null);
    }

    /** Operation 의 사용자(구독자) → {@code creatorUserId}(예측가) 로 {@code subscribe}. @return tx 해시 */
    public String dispatchSubscribe(String operationId, Long creatorUserId, BigInteger amount) {
        return dispatch(operationId, "subscribe", w -> relayer.subscribe(w.self(), w.other(), amount), creatorUserId);
    }

    /** self = Operation 사용자의 지갑, other = 구독일 때 예측가의 지갑(아니면 null). */
    private record Wallets(String self, String other) {}

    private String dispatch(
            String operationId, String what, Function<Wallets, String> call, Long otherUserId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "TokenOperationService.dispatch 는 트랜잭션 밖에서 불러야 한다 — 접수를 먼저 커밋하고 tx 를 보낸다");
        }

        Wallets wallets;
        try {
            wallets = tx.execute(s -> loadWallets(operationId, otherUserId));
        } catch (BusinessException e) {
            // 지갑 미연동·잘못된 예측가 id 도 "이 작업은 못 보낸다" 라서 FAILED 로 닫는다. 작업 자체가 없으면 기록할 행이 없다.
            if (e.getErrorCode() != ErrorCode.OPERATION_NOT_FOUND) {
                fail(operationId, e.getErrorCode().name(), e.getMessage());
            }
            throw e;
        }

        String txHash;
        try {
            txHash = call.apply(wallets);
        } catch (BusinessException e) {
            // 잔액 부족(409) · RPC 장애 / 키 권한(503). 사용자·운영자가 대응할 수 있는 실패 — 그 상태코드 그대로.
            fail(operationId, e.getErrorCode().name(), e.getMessage());
            throw e;
        } catch (TokenRevertException e) {
            // 호출자 검증을 뚫고 온 컨트랙트 거부(서버 버그). 다시 보내도 같다 — 500.
            // 작업 코드도 요청 응답과 같은 INTERNAL_ERROR 로 닫는다(-226). 사용자가 대응할 거부(잔액 부족 · 키 권한)는
            // 릴레이어가 이미 BusinessException 으로 바꿔 위 갈래로 온다 — 여기 오는 이름은 사용자가 알아도 할 게 없다.
            // 이름(SelfSubscribe 등)은 메시지·로그에 남는다.
            fail(operationId, ErrorCode.INTERNAL_ERROR.name(), e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } catch (RuntimeException e) {
            // 예상 못 한 예외. 전역 핸들러가 500 INTERNAL_ERROR 로 내리므로 작업도 같은 코드다 — 클래스명을 코드로 쓰면
            // NullPointerException 이 M-02 화면에 그대로 뜬다(-226). 클래스명·원문은 메시지·로그에.
            fail(operationId, ErrorCode.INTERNAL_ERROR.name(), e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }

        tx.execute(
                s -> {
                    operations.findById(operationId).orElseThrow().markSent(txHash);
                    return null;
                });
        log.info("작업 {} — 토큰 {} tx {}", operationId, what, txHash);
        return txHash;
    }

    /**
     * 지갑을 읽으면서 두 가지를 같이 막는다: 이미 보낸 작업의 재전송(= 이중 소각)과 지갑 미연동.
     * 후자는 {@code SignatureGuard} 가 서명 단계에서 먼저 막지만, 여기는 돈이 나가는 마지막 문이라 한 번 더 본다.
     */
    private Wallets loadWallets(String operationId, Long otherUserId) {
        Operation op =
                operations
                        .findById(operationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_NOT_FOUND));
        if (op.getStatus() != Operation.Status.PENDING || op.getTxHash() != null) {
            throw new IllegalStateException(
                    "작업 " + operationId + " 은 이미 전송됐거나 끝났다(" + op.getStatus() + ", tx " + op.getTxHash() + ")");
        }
        String self = walletOf(op.getUser());
        String other = null;
        if (otherUserId != null) {
            other =
                    walletOf(
                            users.findById(otherUserId)
                                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "creatorUserId")));
        }
        return new Wallets(self, other);
    }

    private static String walletOf(User user) {
        String wallet = user.getWalletAddress();
        if (wallet == null || wallet.isBlank()) {
            throw new BusinessException(ErrorCode.WALLET_NOT_LINKED);
        }
        return wallet;
    }

    /**
     * FAILED 기록. {@code code} 는 닫힌 어휘({@code ErrorCode} 이름)만 받는다. {@code message} 는 운영자용 원문이라 DB·로그에만 남고
     * 응답에는 안 나간다 — {@code OperationResponse} 가 코드로 문구를 만든다. 300자 컬럼에 맞춰 자른다.
     */
    private void fail(String operationId, String code, String message) {
        String trimmed = message == null ? "" : message.length() > 300 ? message.substring(0, 300) : message;
        tx.execute(
                s -> {
                    operations.findById(operationId).ifPresent(op -> op.markFailed(code, trimmed));
                    return null;
                });
        log.warn("작업 {} — 토큰 tx 전송 실패 {}: {}", operationId, code, trimmed);
    }
}
