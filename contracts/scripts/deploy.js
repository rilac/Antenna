const hre = require('hardhat');
const { readArtifact, emit } = require('./emit-artifacts');

/**
 * 로컬 배포 — CommitAnchor v3 (ANT-CHAIN-13) + PredictToken(ANT) (ANT-TOKEN-07).
 *
 *   터미널 1: npx hardhat node
 *   터미널 2: npm run deploy:local
 *
 * Hardhat 기본 signer 0 을 관리자, 1 을 릴레이어, 2 를 수납으로 쓴다. 운영과 같은 역할 분리다 —
 * 릴레이어(1) 한 주소가 CommitAnchor 의 ANCHOR_ROLE 과 PredictToken 의 OPERATOR_ROLE 을 같이 갖는다
 * (운영 결정 ANT-CHAIN-03 plan ⑤). ANCHOR_ADMIN / ANCHOR_RELAYER / TOKEN_TREASURY 환경변수로 덮어쓸 수 있다.
 *
 * <b>배포 순서가 주소를 정한다.</b> 새로 띄운 노드에서 signer 0 의 nonce 0 → CommitAnchor, nonce 1 → PredictToken 이라
 * 노드를 다시 띄워도 두 주소가 늘 같다 — backend/.env 를 매번 고치지 않아도 된다. 순서를 바꾸지 마라.
 *
 * 토큰이 로컬에 없으면 광고 소각 · 가입 보너스 · 구독을 로컬에서 시험할 방법이 없다. 운영 ANT 로 시험하면 흔적이 영원히 남는다.
 *
 * SSAFY 네트워크는 이 스크립트로 못 간다 — Hardhat 2 의 networks.url 이 HTTP 전용인데
 * SSAFY 가 공개한 RPC 는 웹소켓뿐이다. 그쪽은 scripts/deploy-ssafy.mjs · deploy-token-ssafy.mjs 를 쓴다.
 */
async function main() {
  const { ethers, network } = hre;
  const [deployer, second, third] = await ethers.getSigners();

  const admin = process.env.ANCHOR_ADMIN || deployer.address;
  const relayer = process.env.ANCHOR_RELAYER || (second ? second.address : deployer.address);
  const treasury = process.env.TOKEN_TREASURY || (third ? third.address : deployer.address);
  const chainId = Number((await ethers.provider.getNetwork()).chainId);

  console.log(`로컬 배포 → ${network.name} (chainId ${chainId})`);
  console.log(`  배포자   ${deployer.address}`);
  console.log(`  관리자   ${admin}`);
  console.log(`  릴레이어 ${relayer}  (ANCHOR_ROLE · OPERATOR_ROLE)`);
  console.log(`  수납     ${treasury}`);

  // ① CommitAnchor v3 — nonce 0
  const anchorFactory = await ethers.getContractFactory('CommitAnchor');
  const anchor = await anchorFactory.deploy(admin, relayer);
  await anchor.waitForDeployment();
  const anchorTx = anchor.deploymentTransaction();
  const anchorReceipt = await anchorTx.wait();

  emit(
    {
      network: network.name,
      chainId,
      address: await anchor.getAddress(),
      deployer: deployer.address,
      admin,
      relayer,
      txHash: anchorTx.hash,
      blockNumber: anchorReceipt.blockNumber,
    },
    readArtifact(),
    // Hardhat 로컬 노드는 재시작마다 체인이 새로 생긴다 — deployments/local/ 에 덮어쓴다.
    { environment: 'local' },
  );

  // ② PredictToken — nonce 1
  const tokenFactory = await ethers.getContractFactory('PredictToken');
  const token = await tokenFactory.deploy(admin, relayer, treasury);
  await token.waitForDeployment();
  const tokenTx = token.deploymentTransaction();
  const tokenReceipt = await tokenTx.wait();
  const tokenAddress = await token.getAddress();

  // 기록을 남기기 전에 체인에서 직접 읽는다 — SSAFY 토큰 배포(deploy-token-ssafy.mjs)와 같은 확인의 로컬판.
  const facts = {
    name: await token.name(),
    symbol: await token.symbol(),
    decimals: Number(await token.decimals()),
    treasury: await token.treasury(),
    platformShareBps: Number(await token.PLATFORM_SHARE_BPS()),
  };
  const operatorOk = await token.hasRole(await token.OPERATOR_ROLE(), relayer);
  if (facts.decimals !== 0 || facts.symbol !== 'ANT' || facts.treasury.toLowerCase() !== treasury.toLowerCase() || !operatorOk) {
    throw new Error(`PredictToken 확인 실패 — ${JSON.stringify({ ...facts, operatorOk })}. 기록을 남기지 않았다.`);
  }

  emit(
    {
      network: network.name,
      chainId,
      address: tokenAddress,
      deployer: deployer.address,
      admin,
      operator: relayer,
      treasury,
      ...facts,
      txHash: tokenTx.hash,
      blockNumber: tokenReceipt.blockNumber,
    },
    readArtifact('PredictToken'),
    { environment: 'local', contractName: 'PredictToken', version: 1, envVar: 'CONTRACT_PREDICT_TOKEN' },
  );

  console.log('  잔액이 필요하면: MINT_TO=<내 지갑> npm run mint:local  (기본 10000 ANT, reason SIGNUP_BONUS)');
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
