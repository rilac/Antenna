const hre = require('hardhat');
const { readDeployment } = require('./emit-artifacts');

/**
 * 로컬 Hardhat 에서 ANT 를 mint 한다 (ANT-TOKEN-07).
 *
 *   MINT_TO=0x내지갑 [MINT_AMOUNT=10000] [MINT_REASON=SIGNUP_BONUS] npm run mint:local
 *
 * 왜 두나: 소각(광고 · 슬롯 초과)과 구독은 잔액이 있어야 시험된다. 가입 보너스(TOKEN-07)가 붙기 전에도,
 * 붙은 뒤 다른 금액이 필요할 때도 손으로 잔액을 만든다. hardhat run 은 인자를 못 받아 환경변수로 받는다.
 *
 * 보내는 쪽은 signer 1 — 오퍼레이터이자 서버 릴레이어와 같은 주소다(deploy.js). 서버 토큰 인덱서가 Minted 이벤트를 읽어
 * token_ledger 에 쓴다. 그 지갑이 회원에 연동돼 있어야 원장에 잡힌다(아니면 이벤트만 남고 WARN 한 줄).
 *
 * <b>로컬 전용이다.</b> localhost·hardhat 네트워크가 아니면 멈춘다 — 운영 ANT 에 시험 흔적을 남기지 않는다(ANT-CHAIN-12 결정).
 */
async function main() {
  const { ethers, network } = hre;
  if (network.name !== 'localhost' && network.name !== 'hardhat') {
    throw new Error(`로컬 전용 스크립트다. 지금 네트워크: ${network.name}`);
  }

  const to = process.env.MINT_TO;
  if (!to || !ethers.isAddress(to)) {
    throw new Error('MINT_TO 에 받을 지갑 주소를 넣어라. 예) MINT_TO=0x… npm run mint:local');
  }
  const amount = BigInt(process.env.MINT_AMOUNT || '10000');
  const reason = process.env.MINT_REASON || 'SIGNUP_BONUS';

  const { address } = readDeployment('local', 'PredictToken');
  const [, operator] = await ethers.getSigners();
  const token = await ethers.getContractAt('PredictToken', address, operator);

  // 서버 TokenReason.toBytes32 와 같은 형식 — ASCII 왼쪽 정렬, 뒤를 0 으로 채운 32바이트.
  const tx = await token.mint(to, amount, ethers.encodeBytes32String(reason));
  const receipt = await tx.wait();

  console.log(`mint ${amount} ANT → ${to} (${reason})`);
  console.log(`  tx ${tx.hash} · 블록 ${receipt.blockNumber}`);
  console.log(`  잔액 ${await token.balanceOf(to)} ANT`);
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
