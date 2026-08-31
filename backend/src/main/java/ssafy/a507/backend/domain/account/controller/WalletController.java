package ssafy.a507.backend.domain.account.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ssafy.a507.backend.common.security.CurrentUserProvider;
import ssafy.a507.backend.domain.account.dto.WalletLinkRequest;
import ssafy.a507.backend.domain.account.dto.WalletLinkResponse;
import ssafy.a507.backend.domain.account.dto.WalletNonceRequest;
import ssafy.a507.backend.domain.account.dto.WalletNonceResponse;
import ssafy.a507.backend.domain.account.dto.WalletStatusResponse;
import ssafy.a507.backend.domain.account.service.WalletService;

/**
 * 지갑은 계정당 1개인 싱글턴 리소스라 경로가 단수다(API 명세 §지갑·토큰).
 * Base 는 /api/v1 이다 — 명세 헤더와 프론트 api/client.ts 가 그 값으로 고정돼 있다.
 * 잔액·원장 조회(/wallet/balance · /wallet/ledger)는 온체인 대사가 필요해 ANT-TOKEN-04로 뺐다.
 */
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final CurrentUserProvider currentUserProvider;

    /** 지갑 연동뿐 아니라 온체인 동반 요청 전부가 쓰는 공용 창구다. scope가 nonce 칸을 고른다. */
    @PostMapping("/nonce")
    public WalletNonceResponse issueNonce(@Valid @RequestBody WalletNonceRequest request) {
        return walletService.issueNonce(currentUserProvider.currentUserId(), request.scope());
    }

    /** 새 리소스를 만드는 게 아니라 기존 계정의 상태를 바꾸는 전이라서 200이다. */
    @PostMapping("/link")
    public WalletLinkResponse link(@Valid @RequestBody WalletLinkRequest request) {
        return new WalletLinkResponse(
                walletService.link(currentUserProvider.currentUserId(), request));
    }

    @GetMapping
    public WalletStatusResponse status() {
        return walletService.status(currentUserProvider.currentUserId());
    }
}
