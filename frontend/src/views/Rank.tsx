import type { Engine } from '../engine/engine';
import type { Track } from '../engine/types';
import { Badge, Button, DataTable } from '../ui';
import type { Column } from '../ui';
import { Avatar } from './PredCard';

interface Row extends Record<string, unknown> {
  uid: string;
  rank: number;
  name: string;
  handle: string;
  color: string;
  initial: string;
  scoreFmt: string;
  rateFmt: string;
  errFmt: string;
  done: number;
  feeFmt: string;
  isMe: boolean;
  subscribed: boolean;
  onSubscribe: () => void;
  onOpen: () => void;
}

export function Rank({ e }: { e: Engine }) {
  const S = e.state;
  const filter = S.rankTab === 'total' ? null : S.rankTab;
  const track = S.rankTrack;

  const rows: Row[] = e.USERS.map((u) => ({ u, ...e.stats(u.id, filter, track) }))
    .filter((r) => r.done > 0)
    .sort((a, b) => b.score - a.score)
    .map((r, i) => ({
      uid: r.u.id,
      rank: i + 1,
      name: r.u.name,
      handle: r.u.handle,
      color: r.u.color,
      initial: r.u.name[0],
      scoreFmt: r.score.toFixed(1),
      rateFmt: r.rate.toFixed(1) + '%',
      errFmt: r.err.toFixed(2) + '%',
      done: r.done,
      feeFmt: r.u.fee ? e.num(r.u.fee) : '-',
      isMe: r.u.id === 'me',
      subscribed: e.me.subs.has(r.u.id),
      onSubscribe: () => e.askSub(r.u.id),
      onOpen: () => e.go('profile', r.u.id),
    }));

  const columns: Column<Row>[] = [
    { key: 'rank', label: '#', width: 36 },
    {
      key: 'name',
      label: '예측자',
      render: (r) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }} onClick={r.onOpen}>
          <Avatar color={r.color} initial={r.initial} size={28} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13 }}>{r.name}</div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{r.handle}</div>
          </div>
        </div>
      ),
    },
    { key: 'score', label: '신뢰도', align: 'right', render: (r) => <b style={{ color: 'var(--accent)' }}>{r.scoreFmt}</b> },
    { key: 'rate', label: '적중률', align: 'right', render: (r) => r.rateFmt },
    { key: 'err', label: '평균오차', align: 'right', render: (r) => r.errFmt },
    { key: 'done', label: '검증완료', align: 'right', render: (r) => r.done },
    { key: 'fee', label: '월 구독료', align: 'right', render: (r) => r.feeFmt },
    {
      key: 'action',
      label: '',
      align: 'right',
      render: (r) =>
        r.isMe ? null : r.subscribed ? (
          <Badge status="success">구독중</Badge>
        ) : (
          <Button size="sm" variant="accent" onClick={r.onSubscribe}>
            구독
          </Button>
        ),
    },
  ];

  const trackTabs: [Track, string][] = [
    ['REAL', '실전 트랙'],
    ['REPLAY', '리플레이 트랙'],
  ];
  const tabs: [string, string][] = [
    ['total', '종합'],
    ['short', '단기(≤10)'],
    ['long', '장기(≥20)'],
    ...e.SECTORS.map((s) => [s, s] as [string, string]),
  ];

  return (
    <>
      <h2 style={{ margin: '0 0 4px' }}>예측자 랭킹</h2>
      <p style={{ color: 'var(--text-secondary)', fontSize: 13, margin: '0 0 16px' }}>
        신뢰도 = (적중률×0.7 + 목표가 정확도×0.3) × 표본 가중치. 검증 완료 건만 반영됩니다.
      </p>

      <div className="row" style={{ marginBottom: 8, flexWrap: 'wrap' }}>
        {trackTabs.map(([k, label]) => (
          <Button
            key={k}
            size="sm"
            variant={track === k ? 'accent' : 'secondary'}
            onClick={() => e.setState({ rankTrack: k })}
          >
            {label}
          </Button>
        ))}
      </div>
      <div style={{ fontSize: 11.5, color: 'var(--text-muted)', marginBottom: 12 }}>
        {track === 'REAL'
          ? '실제 시장 예측만 집계합니다. 리플레이 실적은 신뢰도 점수에 반영되지 않습니다.'
          : '리플레이 시즌 실적입니다. 합성 경로 위의 기록이라 실전 신뢰도와 별도로 집계됩니다.'}
      </div>

      <div className="row" style={{ marginBottom: 16, flexWrap: 'wrap' }}>
        {tabs.map(([k, label]) => (
          <Button key={k} size="sm" variant={S.rankTab === k ? 'primary' : 'secondary'} onClick={() => e.setState({ rankTab: k })}>
            {label}
          </Button>
        ))}
      </div>

      <div className="card scroll-x" style={{ padding: 8 }}>
        <DataTable columns={columns} rows={rows} rowKey="uid" />
      </div>
    </>
  );
}
