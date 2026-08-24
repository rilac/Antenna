import { buildPredVM, HORIZONS, SLOT_FEE, SLOT_FREE } from '../engine/engine';
import type { Engine } from '../engine/engine';
import type { UiState } from '../engine/types';
import { Badge, Button, Select } from '../ui';
import { PredCard } from './PredCard';

const FEED_TABS: [UiState['feedTab'], string][] = [
  ['all', '전체'],
  ['base', '기준가 대기'],
  ['open', '검증 대기'],
  ['done', '검증 완료'],
  ['sub', '구독 중'],
];

export function Feed({ e }: { e: Engine }) {
  const S = e.state;

  let list = e.preds.slice().sort((a, b) => b.day - a.day || b.id - a.id);
  if (S.feedTab === 'base') list = list.filter((p) => p.status === 'BASE');
  if (S.feedTab === 'open') list = list.filter((p) => p.status === 'OPEN');
  if (S.feedTab === 'done') list = list.filter((p) => p.status === 'HIT' || p.status === 'MISS');
  if (S.feedTab === 'sub') list = list.filter((p) => e.me.subs.has(p.uid));

  const count = (k: UiState['feedTab']) => {
    if (k === 'all') return e.preds.length;
    if (k === 'sub') return e.me.subs.size;
    if (k === 'done') return e.preds.filter((p) => p.status === 'HIT' || p.status === 'MISS').length;
    return e.preds.filter((p) => p.status === k.toUpperCase()).length;
  };

  const used = e.slotUsed('REAL');
  const st = e.ST(S.formCode);

  return (
    <>
      <h2 style={{ margin: '0 0 4px' }}>홈 · 예측 피드</h2>
      <p style={{ color: 'var(--text-secondary)', fontSize: 13, margin: '0 0 16px' }}>
        모든 가격은 직전 영업일 종가입니다. 매 영업일 13:30 배치가 기준가 확정 · 만기 검증 · 머클 앵커를 한 번에
        처리합니다.
      </p>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 12, marginBottom: 16, minWidth: 0 }}>
        <div className="card" style={{ cursor: 'pointer' }} onClick={() => e.go('season')}>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>시즌 진행상황</div>
          <div className="between">
            <b style={{ fontSize: 14 }}>
              Day {e.sDay + 1}/{e.SEASON.len}
            </b>
            <span style={{ fontSize: 12, color: 'var(--accent)' }}>모의투자로 이동 →</span>
          </div>
          <div style={{ height: 6, background: 'var(--divider)', borderRadius: 6, overflow: 'hidden', marginTop: 8 }}>
            <div
              style={{
                height: '100%',
                background: 'var(--accent)',
                width: ((e.sDay / (e.SEASON.len - 1)) * 100).toFixed(1) + '%',
              }}
            />
          </div>
        </div>
        <div className="card">
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>최근 배치 알림</div>
          <div style={{ fontSize: 13 }}>{e.lastBatchNote}</div>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="between" style={{ cursor: 'pointer' }} onClick={() => e.setState({ composerOpen: !S.composerOpen })}>
          <div className="row">
            <b style={{ fontSize: 14 }}>+ 새 예측 등록</b>
            <Badge status={used >= SLOT_FREE ? 'warning' : 'neutral'}>
              오늘 등록 {used} / 무료 {SLOT_FREE}건
              {used >= SLOT_FREE ? ` · 초과분 ${e.num(SLOT_FEE)} PRT` : ''}
            </Badge>
          </div>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{S.composerOpen ? '접기' : '펼치기'}</span>
        </div>

        {S.composerOpen && (
          <>
            <div
              style={{ marginTop: 14, display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 12, minWidth: 0 }}
            >
              <div>
                <label className="f" style={{ marginTop: 0 }}>
                  종목
                </label>
                <Select
                  style={{ width: '100%' }}
                  options={e.STOCKS.map((s) => ({ value: s.c, label: `${s.n} (${s.c})` }))}
                  value={S.formCode}
                  onChange={(v) => e.setState({ formCode: v })}
                />
                <label className="f">방향</label>
                <div className="row">
                  <Button
                    variant={S.dir === 'UP' ? 'primary' : 'secondary'}
                    onClick={() => e.setState({ dir: 'UP' })}
                    style={{ flex: 1 }}
                  >
                    ▲ 상승
                  </Button>
                  <Button
                    variant={S.dir === 'DOWN' ? 'primary' : 'secondary'}
                    onClick={() => e.setState({ dir: 'DOWN' })}
                    style={{ flex: 1 }}
                  >
                    ▼ 하락
                  </Button>
                </div>
                <label className="f">목표가 (직전 종가 {e.num(st.close)})</label>
                <input
                  type="number"
                  placeholder="예: 82000"
                  value={S.formTarget}
                  onChange={(ev) => e.setState({ formTarget: ev.target.value })}
                />
              </div>
              <div>
                <label className="f" style={{ marginTop: 0 }}>
                  예측 기간
                </label>
                <Select
                  style={{ width: '100%' }}
                  options={HORIZONS.map((h) => ({ value: String(h[0]), label: h[1] }))}
                  value={String(S.formHorizon)}
                  onChange={(v) => e.setState({ formHorizon: +v })}
                />
                <label className="f">확신도 {S.formConf}%</label>
                <input
                  type="range"
                  min={50}
                  max={100}
                  value={S.formConf}
                  onChange={(ev) => e.setState({ formConf: +ev.target.value })}
                />
                <label className="f">분석 근거 (구독자에게만 공개)</label>
                <textarea
                  rows={3}
                  placeholder="판단 근거를 적어주세요."
                  value={S.formNote}
                  onChange={(ev) => e.setState({ formNote: ev.target.value })}
                />
              </div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Button variant="primary" onClick={() => e.submitPred()} style={{ width: '100%' }}>
                ⛓ 해시 커밋하고 등록
              </Button>
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>
              본 서비스는 불특정 다수에게 동일한 정보를 단방향으로 제공하며, 개별 종목 매매를 권유하지 않습니다. 투자
              판단과 그 결과는 이용자 본인의 책임입니다.
            </div>
          </>
        )}
      </div>

      <div className="row" style={{ marginBottom: 16, flexWrap: 'wrap' }}>
        {FEED_TABS.map(([k, label]) => (
          <Button
            key={k}
            size="sm"
            variant={S.feedTab === k ? 'primary' : 'secondary'}
            onClick={() => e.setState({ feedTab: k })}
          >
            {label} {count(k)}
          </Button>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr)', gap: 16, minWidth: 0 }}>
        {list.slice(0, 24).map((p) => (
          <PredCard key={p.id} p={buildPredVM(e, p)} />
        ))}
      </div>
    </>
  );
}

export function FeedSidebar({ e }: { e: Engine }) {
  const top5 = e.USERS.filter((u) => u.id !== 'me')
    .map((u) => ({ u, ...e.stats(u.id) }))
    .filter((r) => r.done > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 5);
  const subIds = [...e.me.subs];
  const reports = subIds
    .flatMap((uid) => e.reportsFor(uid).slice(0, 1).map((r) => ({ r, u: e.U(uid) })))
    .sort((a, b) => b.r.day - a.r.day)
    .slice(0, 3);
  const bad = e.audit().some((x) => !x.valid);

  return (
    <div
      style={{
        flex: 'none',
        width: 272,
        overflowY: 'auto',
        padding: '24px 20px',
        borderLeft: '1px solid var(--border)',
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
    >
      <div className="card">
        <b style={{ fontSize: 13 }}>랭킹 TOP5</b>
        <div style={{ display: 'grid', gap: 8, marginTop: 10 }}>
          {top5.map((r) => (
            <div key={r.u.id} className="row" style={{ cursor: 'pointer' }} onClick={() => e.go('profile', r.u.id)}>
              <div
                style={{
                  width: 26,
                  height: 26,
                  borderRadius: 8,
                  background: r.u.color,
                  color: '#fff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 800,
                  fontSize: 11,
                  flex: 'none',
                }}
              >
                {r.u.name[0]}
              </div>
              <div style={{ flex: 1, fontSize: 12.5 }}>{r.u.name}</div>
              <b style={{ fontSize: 12.5, color: 'var(--accent)' }}>{r.score.toFixed(1)}</b>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <b style={{ fontSize: 13 }}>내 구독 목록</b>
        <div style={{ display: 'grid', gap: 8, marginTop: 10 }}>
          {subIds.map((uid) => {
            const u = e.U(uid);
            return (
              <div key={uid} className="row" style={{ cursor: 'pointer' }} onClick={() => e.go('profile', uid)}>
                <div
                  style={{
                    width: 26,
                    height: 26,
                    borderRadius: 8,
                    background: u.color,
                    color: '#fff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 800,
                    fontSize: 11,
                    flex: 'none',
                  }}
                >
                  {u.name[0]}
                </div>
                <div style={{ flex: 1, fontSize: 12.5 }}>{u.name}</div>
              </div>
            );
          })}
          {!subIds.length && <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>구독 중인 예측자가 없습니다.</div>}
        </div>
      </div>

      <div className="card">
        <b style={{ fontSize: 13 }}>📰 구독 리포트</b>
        <div style={{ display: 'grid', gap: 8, marginTop: 10 }}>
          {reports.map((x) => (
            <div
              key={x.r.id}
              style={{ cursor: 'pointer' }}
              onClick={() => e.setState({ route: 'profile', profileId: x.u.id, profileTab: 'reports' })}
            >
              <div style={{ fontSize: 12.5 }}>
                {x.u.name} · {x.r.title}
              </div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{e.dstr(x.r.day)}</div>
            </div>
          ))}
          {!reports.length && (
            <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>구독한 예측자의 리포트가 여기에 표시됩니다.</div>
          )}
        </div>
      </div>

      <div className="card" style={{ cursor: 'pointer' }} onClick={() => e.go('chain')}>
        <div className="row">
          <Badge status={bad ? 'error' : 'success'}>🔒 원장 {bad ? '훼손 감지' : '정상'}</Badge>
        </div>
      </div>
    </div>
  );
}
