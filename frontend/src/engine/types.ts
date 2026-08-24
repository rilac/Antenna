export type Dir = 'UP' | 'DOWN';
export type Track = 'REAL' | 'REPLAY';
export type PredStatus = 'BASE' | 'OPEN' | 'HIT' | 'MISS';
export type BadgeStatus = 'neutral' | 'accent' | 'success' | 'warning' | 'error';

export interface Stock {
  c: string;
  n: string;
  s: string;
  close: number;
  prevClose: number;
}

export interface User {
  id: string;
  name: string;
  handle: string;
  skill: number;
  fee: number;
  main: string;
  bio: string;
  color: string;
  earned: number;
}

export interface Pred {
  id: number;
  uid: string;
  track?: Track;
  code: string;
  dir: Dir;
  refClose: number;
  target: number;
  horizon: number;
  conf: number;
  /** 등록일. 실전은 영업일 오프셋, 리플레이는 게임일 인덱스 */
  day: number;
  baseDay: number;
  entry: number | null;
  settle: number | null;
  note: string;
  status: PredStatus;
  actual: number | null;
  err?: number;
  salt?: string;
  commit?: string;
  reveal?: boolean;
  block?: number;
  block2?: number;
}

export type BlockType =
  | 'GENESIS'
  | 'PREDICT'
  | 'RESULT'
  | 'ANCHOR'
  | 'SUBSCRIBE'
  | 'SEASON'
  | 'SEASON_ENTRY'
  | 'SEASON_REWARD'
  | 'SLOT_FEE'
  | 'BACKTEST'
  | 'CERT';

export interface Block {
  i: number;
  type: BlockType;
  data: Record<string, string | number>;
  prev: string;
  ts: string;
  hash: string;
  anchor?: { idx: number; tx: string } | null;
  /** ANCHOR 블록이 묶은 커밋 인덱스 */
  _members?: number[];
  /** 변조 시연용 원본 백업 */
  _orig?: Record<string, string | number>;
}

export interface Tick {
  id: string;
  sector: string;
  beta: number;
  series: number[];
  real: string;
  qty: number;
  avg: number;
  blind: boolean;
}

export interface Season {
  name: string;
  len: number;
  cash0: number;
  fee: number;
  mode: 'practice' | 'ranked';
  desc: string;
}

export interface Report {
  id: number;
  uid: string;
  title: string;
  day: number;
  body: string;
}

export interface Stats {
  total: number;
  done: number;
  open: number;
  hit: number;
  rate: number;
  err: number;
  score: number;
}

export interface Toast {
  id: number;
  msg: string;
  color: string;
}

export type Route = 'feed' | 'rank' | 'profile' | 'chain' | 'season';

export type Modal =
  | { kind: 'sub'; uid: string }
  | { kind: 'info'; title: string; desc: string; lines: [string, string][]; note: string }
  | null;

export interface UiState {
  route: Route;
  profileId: string | null;
  profileTab: 'preds' | 'reports';
  feedTab: 'all' | 'base' | 'open' | 'done' | 'sub';
  rankTab: string;
  rankTrack: Track;
  chainMore: number;
  formCode: string;
  dir: Dir;
  formTarget: string;
  formHorizon: number;
  formConf: number;
  formNote: string;
  composerOpen: boolean;
  sComposerOpen: boolean;
  sCode: string;
  sDir: Dir;
  sTarget: string;
  sHorizon: number;
  sNote: string;
  modal: Modal;
  toasts: Toast[];
  qtyMap: Record<string, number | string>;
}
