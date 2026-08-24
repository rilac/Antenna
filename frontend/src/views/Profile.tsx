import { BACKTEST_FEE, buildPredVM, CERT_FEE } from '../engine/engine';
import type { Engine } from '../engine/engine';
import { Button, StatCard } from '../ui';
import { Avatar, PredCardCompact } from './PredCard';

export function Profile({ e }: { e: Engine }) {
  const S = e.state;
  const uid = S.profileId || 'u1';
  const u = e.U(uid);
  const s = e.stats(uid);
  const sec = e.sectorStats(uid);
  const rs = e.stats(uid, null, 'REPLAY');
  const subscribed = e.me.subs.has(uid);
  const reports = e.reportsFor(uid);
  const list = e.preds
    .filter((p) => p.uid === uid)
    .sort((a, b) => b.day - a.day)
    .slice(0, 10);

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '340px minmax(0,1fr)', gap: 16, alignItems: 'start', minWidth: 0 }}>
      <div>
        <div className="card">
          <div className="row">
            <Avatar color={u.color} initial={u.name[0]} size={48} />
            <div>
              <b style={{ fontSize: 17 }}>{u.name}</b>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                {u.handle} · 주력 {u.main}
              </div>
            </div>
          </div>
          <p style={{ color: 'var(--text-secondary)', fontSize: 13 }}>{u.bio}</p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 10, minWidth: 0 }}>
            <StatCard label="신뢰도 점수" value={s.score.toFixed(1)} accent />
            <StatCard label="적중률" value={s.rate.toFixed(1) + '%'} />
            <StatCard label="목표가 평균오차" value={s.err.toFixed(2) + '%'} />
            <StatCard label="누적 예측" value={String(s.total)} />
          </div>

          {u.id !== 'me' && (
            <div style={{ marginTop: 14 }}>
              <Button
                variant={subscribed ? 'secondary' : 'accent'}
                onClick={() => (subscribed ? e.unsub(uid) : e.askSub(uid))}
                style={{ width: '100%' }}
              >
                {subscribed ? '구독 중 · 해지하기' : `월 ${e.num(u.fee)} PRT 구독하기`}
              </Button>
              <div style={{ fontSize: 11.5, color: 'var(--text-muted)', marginTop: 8 }}>
                구독료의 70%는 예측자에게 배분됩니다. 누적 배분 {e.num(u.earned)} PRT
              </div>
            </div>
          )}

          <div
            style={{
              marginTop: 14,
              borderTop: '1px solid var(--divider)',
              paddingTop: 14,
              display: 'grid',
              gap: 8,
            }}
          >
            <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>토큰으로 이용하는 분석 도구</div>
            <Button variant="secondary" onClick={() => e.backtest(uid)} style={{ width: '100%' }}>
              📈 팔로우 백테스트 · {e.num(BACKTEST_FEE)} PRT
            </Button>
            <Button variant="secondary" onClick={() => e.certificate(uid)} style={{ width: '100%' }}>
              📜 검증 증명서 발급 · {e.num(CERT_FEE)} PRT
            </Button>
            <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              {rs.done ? `리플레이 트랙 ${rs.done}건 · 적중률 ${rs.rate.toFixed(1)}%` : '리플레이 트랙 기록 없음'} · 실전
              신뢰도와 별도 집계
            </div>
          </div>
        </div>

        <div className="card" style={{ marginTop: 16 }}>
          <b style={{ fontSize: 14 }}>분야별 정확도</b>
          {sec.map((x) => (
            <div key={x.s} style={{ marginTop: 11 }}>
              <div className="between" style={{ fontSize: 12 }}>
                <span>
                  {x.s} <span style={{ color: 'var(--text-muted)' }}>({x.done}건)</span>
                </span>
                <b>{x.rate.toFixed(0)}%</b>
              </div>
              <div style={{ height: 7, background: 'var(--divider)', borderRadius: 6, overflow: 'hidden', marginTop: 4 }}>
                <div style={{ height: '100%', background: 'var(--accent)', width: x.rate.toFixed(0) + '%' }} />
              </div>
            </div>
          ))}
          {!sec.length && <div style={{ color: 'var(--text-muted)', fontSize: 13, marginTop: 8 }}>데이터 없음</div>}
        </div>
      </div>

      <div>
        <div className="row" style={{ marginBottom: 16 }}>
          <Button
            size="sm"
            variant={S.profileTab === 'preds' ? 'primary' : 'secondary'}
            onClick={() => e.setState({ profileTab: 'preds' })}
          >
            예측 이력
          </Button>
          <Button
            size="sm"
            variant={S.profileTab === 'reports' ? 'primary' : 'secondary'}
            onClick={() => e.setState({ profileTab: 'reports' })}
          >
            리포트 ({reports.length})
          </Button>
        </div>

        {S.profileTab !== 'reports' ? (
          <div style={{ display: 'grid', gap: 16 }}>
            {list.map((p) => (
              <PredCardCompact key={p.id} p={buildPredVM(e, p)} />
            ))}
            {!list.length && <div className="card" style={{ color: 'var(--text-muted)' }}>예측 기록이 없습니다.</div>}
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 14 }}>
            {reports.map((r) => (
              <div key={r.id} className="card">
                <div className="between" style={{ marginBottom: 8 }}>
                  <b style={{ fontSize: 15 }}>{r.title}</b>
                  <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>{e.dstr(r.day)}</span>
                </div>
                {subscribed ? (
                  <div style={{ fontSize: 13, color: 'var(--text-body)', lineHeight: 1.7 }}>{r.body}</div>
                ) : (
                  <div style={{ position: 'relative' }}>
                    <div
                      style={{ fontSize: 13, color: 'var(--text-body)', lineHeight: 1.7, maxHeight: 38, overflow: 'hidden' }}
                    >
                      {r.body.slice(0, 40)}…
                    </div>
                    <div
                      style={{
                        position: 'relative',
                        marginTop: -14,
                        height: 34,
                        background: 'linear-gradient(to bottom,transparent,rgba(24,20,32,0.72) 80%)',
                      }}
                    />
                    <div
                      style={{
                        border: '1px dashed var(--border-strong)',
                        borderRadius: 'var(--radius-md)',
                        padding: 12,
                        textAlign: 'center',
                        background: 'var(--surface-sunken)',
                        marginTop: 4,
                      }}
                    >
                      <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                        🔒 구독자만 전체 리포트를 읽을 수 있습니다.
                      </div>
                      <div style={{ marginTop: 8 }}>
                        <Button variant="accent" size="sm" onClick={() => e.askSub(uid)} style={{ width: '100%' }}>
                          구독하고 읽기
                        </Button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            ))}
            {!reports.length && <div className="card" style={{ color: 'var(--text-muted)' }}>등록된 리포트가 없습니다.</div>}
          </div>
        )}
      </div>
    </div>
  );
}
