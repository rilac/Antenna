import fs from 'node:fs';
import path from 'node:path';
import { Wallet } from 'ethers';

/**
 * 키 생성기 (ANT-CHAIN-08).
 *
 *   node scripts/keygen.mjs            → 키 1개
 *   node scripts/keygen.mjs admin      → 라벨만 붙여 출력
 *
 *   node scripts/keygen.mjs --out <파일> prod-admin prod-relayer prod-treasury
 *     → 라벨마다 키 하나. <파일> 에 `PROD_ADMIN_ADDRESS=… / PROD_ADMIN_PRIVATE_KEY=…` 꼴로 쓰고
 *       화면에는 **주소만** 찍는다. 파일이 이미 있으면 거부한다(만든 키를 덮어써 잃지 않게). (ANT-CHAIN-12)
 *
 * 왜 --out 이 생겼나: 운영 키는 화면(터미널 스크롤백·세션 기록)에 비밀키가 남으면 안 된다.
 * <파일> 은 **저장소 밖**에 둔다(예: C:/Users/<me>/antenna-secrets/). 거기서 관리자·수납 키는 비밀번호 관리자로,
 * 릴레이어 키는 GitLab CI/CD 변수로 옮기고 파일은 지운다.
 *
 * 인자 없이 쓰면 예전처럼 화면에 찍는다 — 개발 키용이다.
 * 가스가 0 인 체인이라 잔액을 채울 필요가 없다. 만든 즉시 배포·전송이 된다.
 */
const args = process.argv.slice(2);
const outAt = args.indexOf('--out');

if (outAt < 0) {
  const label = args[0] || 'key';
  const w = Wallet.createRandom();
  console.log(`[${label}]`);
  console.log(`  address     ${w.address}`);
  console.log(`  private key ${w.privateKey}`);
} else {
  const out = args[outAt + 1];
  const labels = args.filter((_, i) => i !== outAt && i !== outAt + 1);
  if (!out || labels.length === 0) {
    console.error('사용법: node scripts/keygen.mjs --out <파일> <라벨> [<라벨> …]');
    process.exit(1);
  }
  if (fs.existsSync(out)) {
    console.error(`${out} 가 이미 있다 — 덮어쓰지 않는다. 그 파일의 키를 쓰거나, 옮긴 뒤 지우고 다시 돌려라.`);
    process.exit(1);
  }
  const lines = [`# keygen.mjs --out 으로 생성 (${new Date().toISOString()}). 저장소에 넣지 않는다.`, ''];
  for (const label of labels) {
    const name = label.toUpperCase().replace(/[^A-Z0-9]/g, '_');
    const w = Wallet.createRandom();
    lines.push(`${name}_ADDRESS=${w.address}`, `${name}_PRIVATE_KEY=${w.privateKey}`, '');
    console.log(`[${label}]  ${w.address}`);
  }
  fs.mkdirSync(path.dirname(path.resolve(out)), { recursive: true });
  // 0600 — 윈도우에서는 무시되지만 리눅스 서버에서 돌릴 때를 위해 둔다.
  fs.writeFileSync(out, lines.join('\n'), { encoding: 'utf8', mode: 0o600 });
  console.log(`비밀키는 ${out} 에만 있다(화면에 출력하지 않음).`);
}
