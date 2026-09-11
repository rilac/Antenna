const fs = require('fs');
const path = require('path');

/**
 * 배포 산출물을 두 곳에 떨군다. 로컬 배포와 SSAFY 배포가 같은 코드를 쓰게 하려고 뺐다.
 *
 *  1. contracts/deployments/<환경>/<Contract>.json — 사람이 읽는 배포 기록 (커밋한다)
 *  2. backend/src/main/resources/abi/<Contract>.json — 서버가 읽는 ABI (커밋한다)
 *
 * ABI 를 런타임에 어디서 받아오지 않고 리소스로 커밋하는 이유: 컨트랙트 소스와 ABI 의
 * 버전이 같이 고정돼야 하고, 받아오는 구조는 부팅에 실패면을 하나 더 만든다.
 *
 * ── 환경 (ANT-CHAIN-12) ─────────────────────────────────────────────
 * 같은 SSAFY 체인에 컨트랙트가 두 벌 산다 — dev(팀원 로컬·Live 테스트)와 prod(운영 서버).
 * 키도 두 벌이고, 역할이 주소 단위로 부여돼 있어 한 환경의 키로 다른 환경의 컨트랙트를 못 움직인다.
 * 그래서 기록을 환경 폴더로 나눠 "이 주소가 어느 환경 것인가"를 파일 위치가 말하게 한다. 표는 deployments/README.md.
 *
 *   dev   — SSAFY. 덮어쓰기 허용(가스 0, 잔액은 테스트 흔적뿐)
 *   prod  — SSAFY. **기록이 있으면 배포 자체를 거부한다.** PredictToken 은 잔액이 사는 곳이고(재배포 금지, ANT-CHAIN-03)
 *           CommitAnchor 는 운영 DB 의 batchId 와 수명이 묶여 있다(README 함정 2)
 *   local — Hardhat 로컬 노드(deploy.js)
 *
 * 예전 이름(`ssafy.json` = CommitAnchor, `ssafy.PredictToken.json`)은 환경을 담지 못해 운영을 배포하면
 * 개발 기록을 덮었다. 지금은 dev/ 로 옮겨져 있다.
 */

const ROOT = path.resolve(__dirname, '..');
const ABI_DIR = path.resolve(ROOT, '..', 'backend', 'src', 'main', 'resources', 'abi');
const DEPLOY_DIR = path.join(ROOT, 'deployments');
const DEFAULT_CONTRACT = 'CommitAnchor';
/** SSAFY 배포 스크립트가 받는 환경. local 은 deploy.js 가 직접 넘긴다. */
const SSAFY_ENVIRONMENTS = ['dev', 'prod'];
/** 기록이 한 번 생기면 다시 배포하지 않는 환경. */
const WRITE_ONCE = new Set(['prod']);

/** 서버가 읽는 ABI 리소스 경로. */
function abiDest(contractName = DEFAULT_CONTRACT) {
  return path.join(ABI_DIR, `${contractName}.json`);
}

/** 배포 기록 경로. */
function deploymentPath(environment, contractName = DEFAULT_CONTRACT) {
  return path.join(DEPLOY_DIR, environment, `${contractName}.json`);
}

/** 배포 기록을 읽는다. 복구·데모 스크립트가 주소를 여기서 가져간다. */
function readDeployment(environment, contractName = DEFAULT_CONTRACT) {
  const p = deploymentPath(environment, contractName);
  if (!fs.existsSync(p)) {
    throw new Error(`배포 기록이 없다: ${path.relative(process.cwd(), p)} — DEPLOY_ENV(${environment})를 확인해라.`);
  }
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

/**
 * SSAFY 배포 스크립트가 <b>tx 를 보내기 전에</b> 부른다. 환경을 모른 채 배포하는 것과 운영 기록을 덮는 것을 여기서 막는다.
 * 배포 뒤에 막으면 컨트랙트는 이미 체인에 생겼는데 기록만 없는 상태가 된다 — 그 주소는 아무도 모르게 된다.
 *
 * @returns 검증된 환경 이름(dev | prod)
 */
function requireEnvironment(contractName = DEFAULT_CONTRACT) {
  const environment = process.env.DEPLOY_ENV;
  if (!SSAFY_ENVIRONMENTS.includes(environment)) {
    console.error(`DEPLOY_ENV 가 없거나 틀렸다(${environment ?? '없음'}). ${SSAFY_ENVIRONMENTS.join(' | ')} 중 하나다.`);
    console.error('  어느 환경인지는 contracts/deployments/README.md 의 표를 보고 고른다. 키도 그 환경 것을 써야 한다.');
    process.exit(1);
  }
  const p = deploymentPath(environment, contractName);
  if (WRITE_ONCE.has(environment) && fs.existsSync(p)) {
    console.error(`${path.relative(process.cwd(), p)} 가 이미 있다. ${environment} 환경은 다시 배포하지 않는다.`);
    console.error('  잔액(PredictToken)·batchId(CommitAnchor)가 그 주소에 묶여 있다. deployments/README.md "운영 컨트랙트를 바꿔야 할 때" 를 먼저 읽어라.');
    process.exit(1);
  }
  return environment;
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
 * @param {object} opts { environment(필수: dev | prod | local), contractName = 'CommitAnchor', version = 2, envVar = 'CONTRACT_COMMIT_ANCHOR' }
 */
function emit(info, artifact, opts = {}) {
  const environment = opts.environment;
  if (!environment) {
    throw new Error('emit 에 environment 가 없다 — 어느 폴더에 기록할지 모른다.');
  }
  const contractName = opts.contractName || DEFAULT_CONTRACT;
  const version = opts.version ?? 2; // CommitAnchor v2(ANT-CHAIN-08). v1 배포본과 ABI 가 다르다.
  const envVar = opts.envVar || 'CONTRACT_COMMIT_ANCHOR';

  const deployPath = deploymentPath(environment, contractName);
  // requireEnvironment 가 배포 전에 이미 막았다. 여기는 스크립트가 그걸 빼먹었을 때의 마지막 방어선이다.
  if (WRITE_ONCE.has(environment) && fs.existsSync(deployPath)) {
    throw new Error(`${deployPath} 가 이미 있다 — ${environment} 기록은 덮어쓰지 않는다. 방금 배포된 주소: ${info.address}`);
  }
  fs.mkdirSync(path.dirname(deployPath), { recursive: true });

  const record = {
    contract: contractName,
    version,
    environment,
    ...info,
    deployedAt: new Date().toISOString(),
  };
  fs.writeFileSync(deployPath, JSON.stringify(record, null, 2) + '\n', 'utf8');

  // ABI 는 환경과 무관하다. 어느 경로로 배포하든 같은 파일을 덮어쓴다.
  const abiPath = emitAbi(artifact, contractName);

  console.log('');
  console.log('  주소      ', info.address);
  console.log('  환경      ', `${environment} · ${info.network} (chainId ${info.chainId})`);
  console.log('  배포 기록 ', path.relative(process.cwd(), deployPath));
  console.log('  ABI       ', path.relative(process.cwd(), abiPath));
  console.log('');
  if (environment === 'prod') {
    console.log('  다음: .gitlab-ci.yml 의 .env 블록(운영 고정값)과 deployments/README.md 표를 이 값으로 맞춰라');
  } else {
    console.log('  다음: backend/.env 에 아래 줄을 넣어라');
  }
  console.log(`    ${envVar}=${info.address}`);
  console.log('');
}

module.exports = {
  readArtifact,
  readDeployment,
  requireEnvironment,
  deploymentPath,
  emit,
  emitAbi,
  abiDest,
  ABI_DEST: abiDest(),
};
