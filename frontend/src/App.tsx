import mascot from './assets/mascot.png';
import type { Route } from './engine/types';
import { useEngine } from './useEngine';
import { Badge, Button, Modal } from './ui';
import { Chain } from './views/Chain';
import { Feed, FeedSidebar } from './views/Feed';
import { Profile } from './views/Profile';
import { Rank } from './views/Rank';
import { Season } from './views/Season';

const NAV: [Route, string][] = [
  ['feed', '홈'],
  ['rank', '예측자 랭킹'],
  ['season', '모의투자'],
];

const BREADCRUMB: Record<Route, string> = {
  feed: '홈 · 예측 피드',
  rank: '예측자 랭킹',
  profile: '예측자 프로필',
  chain: '커밋 원장',
  season: '리플레이 투자',
};

export default function App() {
  const e = useEngine();
  const S = e.state;
  const activeKey = S.route === 'profile' || S.route === 'chain' ? '' : S.route;
  const modal = S.modal;
  const subUser = modal?.kind === 'sub' ? e.U(modal.uid) : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', width: '100vw', overflow: 'hidden', boxSizing: 'border-box' }}>
      <div
        className="card"
        style={{
          flex: 'none',
          borderRadius: 0,
          borderBottom: '1px solid var(--border)',
          display: 'flex',
          alignItems: 'center',
          gap: 20,
          padding: '10px 24px',
          overflowX: 'auto',
        }}
      >
        <div className="row" style={{ flex: 'none', gap: 10 }}>
          <img src={mascot} alt="마스코트" className="glow" style={{ width: 40, height: 40, objectFit: 'contain' }} />
          <div className="brandtitle">PredictChain</div>
        </div>
        <nav style={{ display: 'flex', gap: 6, flex: 'none' }}>
          {NAV.map(([k, label]) => (
            <Button key={k} size="sm" variant={activeKey === k ? 'primary' : 'secondary'} onClick={() => e.go(k)}>
              {label}
            </Button>
          ))}
        </nav>
        <div style={{ flex: 1 }} />
        <div style={{ flex: 'none', color: 'var(--text-secondary)', fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap' }}>
          {BREADCRUMB[S.route]}
        </div>
        <div className="row" style={{ flex: 'none' }}>
          <Badge status="neutral">
            영업일 D+{e.DAY} · {e.dstr(e.DAY)}
          </Badge>
          <Badge status="accent">{e.num(e.me.tokens)} PRT</Badge>
          <Button variant="accent" size="sm" onClick={() => e.runBatch(1)}>
            다음 배치
          </Button>
          <Button variant="secondary" size="sm" onClick={() => e.runBatch(5)}>
            5영업일
          </Button>
        </div>
      </div>

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', minWidth: 0 }}>
        <div style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', padding: 24, minWidth: 0 }}>
          {S.route === 'feed' && <Feed e={e} />}
          {S.route === 'rank' && <Rank e={e} />}
          {S.route === 'profile' && <Profile e={e} />}
          {S.route === 'chain' && <Chain e={e} />}
          {S.route === 'season' && <Season e={e} />}
        </div>
        {S.route === 'feed' && <FeedSidebar e={e} />}
      </div>

      <Modal
        open={!!modal}
        title={modal?.kind === 'info' ? modal.title : subUser ? `${subUser.name} 구독` : ''}
        description={modal?.kind === 'info' ? modal.desc : (subUser?.bio ?? '')}
        primaryLabel={
          modal?.kind === 'info' ? '확인' : subUser && e.me.tokens < subUser.fee ? '포인트 부족' : '구독 결제'
        }
        secondaryLabel="취소"
        onPrimary={() => {
          if (!modal) return;
          if (modal.kind === 'info') e.closeModal();
          else e.doSub(modal.uid);
        }}
        onSecondary={() => e.closeModal()}
        onClose={() => e.closeModal()}
      >
        {subUser && (
          <>
            <div className="row" style={{ marginBottom: 10 }}>
              <img src={mascot} alt="마스코트" style={{ width: 44, height: 44, objectFit: 'contain' }} />
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                구독하면 이 예측자의 분석 근거와 리포트를 모두 볼 수 있어요.
              </div>
            </div>
            <div
              style={{
                background: 'var(--surface-sunken)',
                borderRadius: 'var(--radius-md)',
                padding: 14,
                fontSize: 13,
                display: 'grid',
                gap: 6,
              }}
            >
              <div className="between">
                <span style={{ color: 'var(--text-muted)' }}>월 구독료</span>
                <b>{e.num(subUser.fee)} PRT</b>
              </div>
              <div className="between">
                <span style={{ color: 'var(--text-muted)' }}>예측자 배분(70%)</span>
                <b style={{ color: 'var(--accent)' }}>{e.num(Math.round(subUser.fee * 0.7))} PRT</b>
              </div>
              <div className="between" style={{ borderTop: '1px solid var(--divider)', paddingTop: 6 }}>
                <span style={{ color: 'var(--text-muted)' }}>결제 후 잔액</span>
                <b>{e.num(e.me.tokens - subUser.fee)} PRT</b>
              </div>
            </div>
          </>
        )}
        {modal?.kind === 'info' && (
          <>
            <div
              style={{
                background: 'var(--surface-sunken)',
                borderRadius: 'var(--radius-md)',
                padding: 14,
                fontSize: 13,
                display: 'grid',
                gap: 6,
              }}
            >
              {modal.lines.map(([label, value]) => (
                <div key={label} className="between">
                  <span style={{ color: 'var(--text-muted)' }}>{label}</span>
                  <b>{value}</b>
                </div>
              ))}
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 10 }}>{modal.note}</div>
          </>
        )}
      </Modal>

      <div style={{ position: 'fixed', right: 20, bottom: 20, zIndex: 60, display: 'flex', flexDirection: 'column', gap: 8 }}>
        {S.toasts.map((t) => (
          <div
            key={t.id}
            className="card"
            style={{ padding: '11px 15px', fontSize: 13, maxWidth: 320, borderLeft: `3px solid ${t.color}` }}
          >
            {t.msg}
          </div>
        ))}
      </div>
    </div>
  );
}
