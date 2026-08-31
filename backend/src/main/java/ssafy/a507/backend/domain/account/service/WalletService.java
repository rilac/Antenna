package ssafy.a507.backend.domain.account.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.a507.backend.common.error.BusinessException;
import ssafy.a507.backend.common.error.ErrorCode;
import ssafy.a507.backend.common.security.SignatureGuard;
import ssafy.a507.backend.common.security.SignatureNonceStore;
import ssafy.a507.backend.domain.account.dto.WalletLinkRequest;
import ssafy.a507.backend.domain.account.dto.WalletStatusResponse;
import ssafy.a507.backend.domain.account.entity.User;
import ssafy.a507.backend.domain.account.repository.UserRepository;

/** SSAFY WALLET 연동 (ANT-AUTH-04). 연동 후에야 예측 등록과 온체인 동반 요청이 열린다. */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepository;
    private final SignatureNonceStore nonceStore;
    private final SignatureGuard signatureGuard;

    /** 재발급하면 이전 nonce는 죽는다. 탭 두 개에서 각각 받으면 먼저 받은 쪽이 만료된다. */
    public String issueNonce(Long userId) {
        return nonceStore.issue(userId);
    }

    /**
     * 연동 순서가 곧 방어 순서다.
     * ① 이미 연동했나 ② 남이 쓰는 주소인가 ③ 서명이 맞나 — 성공할 수 없는 요청에 nonce를 태우지 않는다.
     */
    @Transactional
    public String link(Long userId, WalletLinkRequest request) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getWalletAddress() != null) {
            throw new BusinessException(ErrorCode.WALLET_ALREADY_LINKED);
        }

        String address = request.address().toLowerCase(Locale.ROOT);
        if (userRepository.existsByWalletAddress(address)) {
            throw new BusinessException(ErrorCode.WALLET_ALREADY_LINKED, "address");
        }

        String recovered = signatureGuard.recover(userId, request);
        // 서명한 지갑과 등록하려는 주소가 같아야 한다. 남의 주소를 자기 계정에 붙이는 걸 막는다.
        if (!address.equals(recovered)) {
            throw new BusinessException(ErrorCode.SIGNER_MISMATCH, "signature");
        }

        user.linkWallet(address);
        try {
            // 선검사와 커밋 사이에 낀 동시 요청은 UQ가 잡는다. flush를 당겨 여기서 받아 낸다.
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.WALLET_ALREADY_LINKED, "address");
        }
        return address;
    }

    @Transactional(readOnly = true)
    public WalletStatusResponse status(Long userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        String address = user.getWalletAddress();
        return new WalletStatusResponse(address != null, address);
    }
}
