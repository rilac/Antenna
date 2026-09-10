const fs = require('fs');
const path = require('path');

/**
 * 배포 산출물을 두 곳에 떨군다. 로컬 배포와 SSAFY 배포가 같은 코드를 쓰게 하려고 뺐다.
 *
 *  1. contracts/deployments/<network>[.<Contract>].json — 사람이 읽는 배포 기록 (커밋한다)
 *  2. backend/src/main/resources/abi/<Contract>.json      — 서버가 읽는 ABI (커밋한다)
 *
 * ABI 를 런타임에 어디서 받아오지 않고 리소스로 커밋하는 이유: 컨트랙트 소스와 ABI 의
 * 버전이 같이 고정돼야 하고, 받아오는 구조는 부팅에 실패면을 하나 더 만든다.
 *
 * 컨트랙트 이름을 받는다(ANT-CHAIN-03 에서 확장). 기본값은 CommitAnchor — 기존 호출(deploy.js ·
 * deploy-ssafy.mjs)은 인자 없이 그대로 동작한다. CommitAnchor 의 배포 기록 파일명은 `<network>.json`
 * 으로 남겨 두고(README·yaml·Live 테스트가 그 경로를 본다), 다른 컨트랙트는 `<network>.<Contract>.json` 이다.
 */

const ROOT = path.resolve(__dirname, '..');
const ABI_DIR = path.resolve(ROOT, '..', 'backend', 'src', 'main', 'resources', 'abi');
const DEFAULT_CONTRACT = 'CommitAnchor';

/** 서버가 읽는 ABI 리소스 경로. */
function abiDest(contractName = DEFAULT_CONTRACT) {
  return path.join(ABI_DIR, `${contractName}.json`);
}

/** artifacts/ 에서 컴파일 산출물을 읽는다. hardhat compile 을 먼저 돌려야 한다. */
function readArtifact(contractName = DEFAULT_CONTRACT) {
  const p = path.join(ROOT, 'artifacts', 'contracts', `${contractName}.sol`, `${contractName}.json`);
  if (!fs.existsSync(p)) {
    throw new Error(`컴파일 산출물이 없다. 먼저 'npx hardhat compile' 을 돌려라: ${p}`);
  }
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

/** ABI 만 리소스로 복사한다. 배포 없이 서버 테스트가 컨트랙트 표면을 고정할 때도 쓴다. */
function emitAbi(artifact, contractName = DEFAULT_CONTRACT) {
  const dest = abiDest(contractName);
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.writeFileSync(dest, JSON.stringify({ contractName, abi: artifact.abi }, null, 2) + '\n', 'utf8');
  return dest;
}

/**
 * @param {object} info { network, chainId, address, deployer, admin, txHash, blockNumber, ... 컨트랙트별 추가 필드 }
 * @param {object} artifact hardhat 컴파일 산출물
 * @param {object} [opts]  { contractName = 'CommitAnchor', version = 2, envVar = 'CONTRACT_COMMIT_ANCHOR' }
 */
function emit(info, artifact, opts = {}) {
  const contractName = opts.contractName || DEFAULT_CONTRACT;
  const version = opts.version ?? 2; // CommitAnchor v2(ANT-CHAIN-08). v1 배포본과 ABI 가 다르다.
  const envVar = opts.envVar || 'CONTRACT_COMMIT_ANCHOR';

  const deployDir = path.join(ROOT, 'deployments');
  fs.mkdirSync(deployDir, { recursive: true });

  const record = {
    contract: contractName,
    version,
    ...info,
    deployedAt: new Date().toISOString(),
  };
  const fileName =
    contractName === DEFAULT_CONTRACT ? `${info.network}.json` : `${info.network}.${contractName}.json`;
  const deployPath = path.join(deployDir, fileName);
  fs.writeFileSync(deployPath, JSON.stringify(record, null, 2) + '\n', 'utf8');

  // ABI 는 네트워크와 무관하다. 어느 경로로 배포하든 같은 파일을 덮어쓴다.
  const abiPath = emitAbi(artifact, contractName);

  console.log('');
  console.log('  주소      ', info.address);
  console.log('  네트워크  ', `${info.network} (chainId ${info.chainId})`);
  console.log('  배포 기록 ', path.relative(process.cwd(), deployPath));
  console.log('  ABI       ', path.relative(process.cwd(), abiPath));
  console.log('');
  console.log('  다음: backend/.env 에 아래 줄을 넣어라');
  console.log(`    ${envVar}=${info.address}`);
  console.log('');
}

module.exports = { readArtifact, emit, emitAbi, abiDest, ABI_DEST: abiDest() };
