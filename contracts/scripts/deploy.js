const hre = require('hardhat');
const { readArtifact, emit } = require('./emit-artifacts');

/**
 * 로컬 배포 (ANT-CHAIN-01).
 *
 *   터미널 1: npx hardhat node
 *   터미널 2: npm run deploy:local
 *
 * SSAFY 네트워크는 이 스크립트로 못 간다 — Hardhat 2 의 networks.url 이 HTTP 전용인데
 * SSAFY 가 공개한 RPC 는 웹소켓뿐이다. 그쪽은 scripts/deploy-ssafy.mjs 를 쓴다.
 */
async function main() {
  const { ethers, network } = hre;
  const [deployer] = await ethers.getSigners();

  // 관리자는 인자로 받는다. 안 주면 배포자가 관리자를 겸한다(로컬 기본).
  const admin = process.env.ANCHOR_ADMIN || deployer.address;

  console.log(`CommitAnchor 배포 → ${network.name}`);
  console.log(`  배포자  ${deployer.address}`);
  console.log(`  관리자  ${admin}`);

  const factory = await ethers.getContractFactory('CommitAnchor');
  const contract = await factory.deploy(admin);
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
