import { Wallet } from 'ethers';

/**
 * 키 생성기 (ANT-CHAIN-08).
 *
 *   node scripts/keygen.mjs            → 키 1개
 *   node scripts/keygen.mjs admin      → 라벨만 붙여 출력
 *
 * CommitAnchor v2 는 키가 두 개다 — 관리자(DEFAULT_ADMIN_ROLE, 서버 밖)와 릴레이어(ANCHOR_ROLE, 서버 .env).
 * 이 스크립트는 파일에 아무것도 쓰지 않는다. 출력을 보고 사람이 옮긴다:
 *   관리자 키   → 비밀번호 관리자. 저장소·서버에 두지 않는다
 *   릴레이어 키 → backend/.env 의 RELAYER_PRIVATE_KEY
 * 가스가 0 인 체인이라 잔액을 채울 필요가 없다. 만든 즉시 배포·전송이 된다.
 */
const label = process.argv[2] || 'key';
const w = Wallet.createRandom();
console.log(`[${label}]`);
console.log(`  address     ${w.address}`);
console.log(`  private key ${w.privateKey}`);
