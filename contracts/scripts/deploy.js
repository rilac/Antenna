const hre = require('hardhat');
const { readArtifact, emit } = require('./emit-artifacts');

/**
 * 로컬 배포 (ANT-CHAIN-08, v2).
 *
 *   터미널 1: npx hardhat node
 *   터미널 2: npm run deploy:local
 *
 * Hardhat 기본 signer 0 을 관리자, 1 을 릴레이어로 쓴다 — SSAFY 배포와 같은 역할 분리를
 * 로컬에서도 그대로 재현하기 위해서다. ANCHOR_ADMIN / ANCHOR_RELAYER 환경변수로 덮어쓸 수 있다.
 *
 * SSAFY 네트워크는 이 스크립트로 못 간다 — Hardhat 2 의 networks.url 이 HTTP 전용인데
 * SSAFY 가 공개한 RPC 는 웹소켓뿐이다. 그쪽은 scripts/deploy-ssafy.mjs 를 쓴다.
 */
async function main() {
  const { ethers, network } = hre;
  const [deployer, second] = await ethers.getSigners();

  const admin = process.env.ANCHOR_ADMIN || deployer.address;
  const relayer = process.env.ANCHOR_RELAYER || (second ? second.address : deployer.address);

  console.log(`CommitAnchor v2 배포 → ${network.name}`);
  console.log(`  배포자   ${deployer.address}`);
  console.log(`  관리자   ${admin}`);
  console.log(`  릴레이어 ${relayer}`);

  const factory = await ethers.getContractFactory('CommitAnchor');
  const contract = await factory.deploy(admin, relayer);
  await contract.waitForDeployment();

  const address = await contract.getAddress();
  const tx = contract.deploymentTransaction();
  const receipt = await tx.wait();

  emit(
    {
      network: network.name,
      chainId: Number((await ethers.provider.getNetwork()).chainId),
      address,
      deployer: deployer.address,
      admin,
      relayer,
      txHash: tx.hash,
      blockNumber: receipt.blockNumber,
    },
    readArtifact(),
  );
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
