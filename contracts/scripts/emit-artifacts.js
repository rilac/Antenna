const fs = require('fs');
const path = require('path');

/**
 * 배포 산출물을 두 곳에 떨군다. 로컬 배포와 SSAFY 배포가 같은 코드를 쓰게 하려고 뺐다.
 *
 *  1. contracts/deployments/<network>.json  — 사람이 읽는 배포 기록 (커밋한다)
 *  2. backend/src/main/resources/abi/CommitAnchor.json — 서버가 읽는 ABI (커밋한다)
 *
 * ABI 를 런타임에 어디서 받아오지 않고 리소스로 커밋하는 이유: 컨트랙트 소스와 ABI 의
 * 버전이 같이 고정돼야 하고, 받아오는 구조는 부팅에 실패면을 하나 더 만든다.
 */

const ROOT = path.resolve(__dirname, '..');
const ABI_DEST = path.resolve(
  ROOT,
  '..',
  'backend',
  'src',
  'main',
  'resources',
  'abi',
  'CommitAnchor.json',
);

/** artifacts/ 에서 컴파일 산출물을 읽는다. hardhat compile 을 먼저 돌려야 한다. */
function readArtifact() {
  const p = path.join(
    ROOT,
    'artifacts',
    'contracts',
    'CommitAnchor.sol',
    'CommitAnchor.json',
  );
  if (!fs.existsSync(p)) {
    throw new Error(`컴파일 산출물이 없다. 먼저 'npx hardhat compile' 을 돌려라: ${p}`);
  }
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

/**
 * @param {object} info { network, chainId, address, deployer, admin, txHash, blockNumber }
 */
function emit(info, artifact) {
  const deployDir = path.join(ROOT, 'deployments');
  fs.mkdirSync(deployDir, { recursive: true });

  const record = {
    contract: 'CommitAnchor',
    ...info,
    deployedAt: new Date().toISOString(),
  };
  const deployPath = path.join(deployDir, `${info.network}.json`);
  fs.writeFileSync(deployPath, JSON.stringify(record, null, 2) + '\n', 'utf8');

  // ABI 는 네트워크와 무관하다. 어느 경로로 배포하든 같은 파일을 덮어쓴다.
  fs.mkdirSync(path.dirname(ABI_DEST), { recursive: true });
  fs.writeFileSync(
    ABI_DEST,
    JSON.stringify({ contractName: 'CommitAnchor', abi: artifact.abi }, null, 2) + '\n',
    'utf8',
  );

  console.log('');
  console.log('  주소      ', info.address);
  console.log('  네트워크  ', `${info.network} (chainId ${info.chainId})`);
  console.log('  배포 기록 ', path.relative(process.cwd(), deployPath));
  console.log('  ABI       ', path.relative(process.cwd(), ABI_DEST));
  console.log('');
  console.log('  다음: backend/.env 에 아래 줄을 넣어라');
  console.log(`    CONTRACT_COMMIT_ANCHOR=${info.address}`);
  console.log('');
}

module.exports = { readArtifact, emit, ABI_DEST };
