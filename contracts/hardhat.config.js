require('@nomicfoundation/hardhat-ethers');
require('@nomicfoundation/hardhat-chai-matchers');

/**
 * 앤테나 컨트랙트 빌드 설정 (ANT-CHAIN-01).
 *
 * 여기서 제일 중요한 줄은 evmVersion 하나다. 아래 주석을 지우지 말 것.
 */
module.exports = {
  solidity: {
    version: '0.8.28',
    settings: {
      // ★ 이 줄을 빼면 SSAFY 네트워크 배포가 revert 한다. ★
      //
      // solc 0.8.20+ 는 기본으로 PUSH0 opcode 를 뱉는데(Shanghai 도입),
      // SSAFY 체인의 EVM 은 Paris 세대라 PUSH0 를 모른다.
      // 증상이 고약하다 — 컴파일은 아무 경고 없이 성공하고, 배포 트랜잭션만
      // "transaction execution reverted" / "missing revert data" 로 죽는다.
      // 컨트랙트 코드를 아무리 들여다봐도 원인이 안 보인다.
      //
      // 실측으로 확인한 것: cancun ✗ / shanghai ✗ / paris ✓ / london ✓
      // (docs-personal/CHAIN-onchain-backend/토큰증권_체인접속_검증정보.md §6-A)
      evmVersion: 'paris',
      optimizer: { enabled: true, runs: 200 },
    },
  },

  networks: {
    // 인프로세스 네트워크. `hardhat test` 가 쓴다 — RPC 가 필요 없다. `npx hardhat node` 도 이 설정으로 뜬다.
    //
    // initialBaseFeePerGas 0 — 서버 TxSender 는 SSAFY 규칙대로 gasPrice 0 으로 서명한다. 기본값(base fee 1 gwei 근처)이면
    // 로컬 Hardhat 이 "maxFeePerGas (0) is too low" 로 전부 거부해, 로컬 개발(ANT-CHAIN-12 결정: 로컬은 Hardhat)에서
    // 앵커·토큰 tx 가 한 건도 안 나간다. base fee 를 0 으로 두면 SSAFY 와 같은 "가스 공짜" 체인이 된다(ANT-CHAIN-13 에서 발견).
    hardhat: { chainId: 31337, initialBaseFeePerGas: 0 },
    // `npx hardhat node` 로 띄운 로컬 체인.
    localhost: { url: 'http://127.0.0.1:8545', chainId: 31337 },
    //
    // SSAFY 네트워크(chainId 31221)는 여기 못 넣는다.
    // 공개된 RPC 가 wss://ws.ssafy-blockchain.com 웹소켓 하나뿐인데
    // Hardhat 2 의 networks.url 은 HTTP 전용이다.
    // → 배포는 scripts/deploy-ssafy.mjs 가 ethers 로 직접 붙는다.
    //   컴파일 산출물(artifacts/)은 이 설정으로 만든 것을 그대로 읽으므로
    //   evmVersion 이 두 경로에서 갈릴 일은 없다.
  },
};
