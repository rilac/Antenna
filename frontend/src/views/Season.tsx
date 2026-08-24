import { buildPredVM, SEASON_HORIZONS, SLOT_FREE } from '../engine/engine';
import type { Engine } from '../engine/engine';
import { Badge, Button, Select, StatCard } from '../ui';
import { PredTile } from './PredCard';

export function Season({ e }: { e: Engine }) {
  const S = e.state;
  const val = e.sValue();
  const total = e.sCash + val;
  const ret = ((total - e.SEASON.cash0) / e.SEASON.cash0) * 100;
  const sUsed = e.slotUsed('REPLAY');
  const sCode = S.sCode || e.TICK[0].id;
  const rp = e.preds.filter((p) => e.isReplay(p)).sort((a, b) => b.day - a.day || b.id - a.id);
  const rdone = rp.filter((p) => p.status === 'HIT' || p.status === 'MISS');

  const modeTabs: ['practice' | 'ranked', string][] = [
    ['practice', '연습 시즌 (실제 구간·공개)'],
    ['ranked', '랭킹 시즌 (부트스트랩·익명·참가비 1,000)'],
  ];

  return (
    <>
      <h2 style={{ margin: '0 0 4px' }}>{e.SEASON.name}</h2>
      <p style={{ color: 'var(--text-secondary)', fontSize: 13, margin: '0 0 12px' }}>{e.SEASON.desc}</p>

      <div className="row" style={{ marginBottom: 16, flexWrap: 'wrap' }}>
        {modeTabs.map(([k, label]) => (
          <Button
            key={k}
            size="sm"
            variant={e.seasonMode === k ? 'accent' : 'secondary'}
            onClick={() => e.switchSeason(k)}
          >
            {label}
          </Button>
        ))}
        <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
          {e.seasonMode === 'ranked'
            ? '블록 부트스트랩 경로 · 종목명 블라인드 · 시즌 상금 지급'
            : '실제 과거 구간 그대로 · 종목명 공개 · 보상 없음'}
        </span>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="between" style={{ flexWrap: 'wrap', marginBottom: 10 }}>
          <b style={{ fontSize: 15 }}>
            Day {e.sDay + 1} / {e.SEASON.len}{' '}
            <span style={{ color: 'var(--text-muted)', fontSize: 12, fontWeight: 400 }}>
              · {e.seasonMode === 'ranked' ? '종목명 블라인드' : '종목명 공개'} · 당일 종가 체결 · 시즌 종료 시 자동 청산
            </span>
          </b>
          <div className="row">
            <Button variant="secondary" size="sm" onClick={() => e.togglePlay()}>
              {e.playing ? '⏸ 일시정지' : '▶ 재생'}
            </Button>
            <Button variant="ghost" size="sm" onClick={() => e.seasonStep()}>
              ▶| 1일
            </Button>
            <Button variant="ghost" size="sm" onClick={() => e.resetSeason()}>
              ↺ 재시작
            </Button>
          </div>
        </div>
        <div style={{ height: 8, background: 'var(--divider)', borderRadius: 6, overflow: 'hidden' }}>
          <div
            style={{
              height: '100%',
              background: 'var(--accent)',
              width: ((e.sDay / (e.SEASON.len - 1)) * 100).toFixed(1) + '%',
            }}
          />
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="between" style={{ cursor: 'pointer' }} onClick={() => e.setState({ sComposerOpen: !S.sComposerOpen })}>
          <div className="row">
            <b style={{ fontSize: 14 }}>+ 이 시장에 예측 등록</b>
            <Badge status="neutral">
              Day {e.sDay + 1} 등록 {sUsed} / 무료 {SLOT_FREE}건
            </Badge>
          </div>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{S.sComposerOpen ? '접기' : '펼치기'}</span>
        </div>
        <div style={{ fontSize: 11.5, color: 'var(--text-muted)', marginTop: 6 }}>
          동일한 예측 검증 엔진이 리플레이 시계 위에서 그대로 동작합니다. 기준가는 다음 게임일 종가로 확정되고, 만기에
          자동 검증됩니다.
        </div>

        {S.sComposerOpen && (
          <>
            <div style={{ marginTop: 14, display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 12, minWidth: 0 }}>
              <div>
                <label className="f" style={{ marginTop: 0 }}>
                  종목
                </label>
                <Select
                  style={{ width: '100%' }}
                  options={e.TICK.map((t) => ({ value: t.id, label: `${t.id} (${t.sector})` }))}
                  value={sCode}
                  onChange={(v) => e.setState({ sCode: v })}
                />
                <label className="f">방향</label>
                <div className="row">
                  <Button
                    variant={S.sDir === 'UP' ? 'primary' : 'secondary'}
                    onClick={() => e.setState({ sDir: 'UP' })}
                    style={{ flex: 1 }}
                  >
                    ▲ 상승
                  </Button>
                  <Button
                    variant={S.sDir === 'DOWN' ? 'primary' : 'secondary'}
                    onClick={() => e.setState({ sDir: 'DOWN' })}
                    style={{ flex: 1 }}
                  >
                    ▼ 하락
                  </Button>
                </div>
                <label className="f">목표가 (당일 종가 {e.num(e.sPrice(e.TK(sCode)))})</label>
                <input
                  type="number"
                  placeholder="예: 32000"
                  value={S.sTarget}
                  onChange={(ev) => e.setState({ sTarget: ev.target.value })}
                />
              </div>
              <div>
                <label className="f" style={{ marginTop: 0 }}>
                  예측 기간
                </label>
                <Select
                  style={{ width: '100%' }}
                  options={SEASON_HORIZONS.map((h) => ({ value: String(h[0]), label: h[1] }))}
                  value={String(S.sHorizon)}
                  onChange={(v) => e.setState({ sHorizon: +v })}
                />
                <label className="f">분석 근거 (구독자에게만 공개)</label>
                <textarea
                  rows={4}
                  placeholder="차트·섹터·베타 기준 판단 근거"
                  value={S.sNote}
                  onChange={(ev) => e.setState({ sNote: ev.target.value })}
                />
              </div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Button variant="primary" onClick={() => e.submitPredReplay()} style={{ width: '100%' }}>
                ⛓ 해시 커밋하고 등록
              </Button>
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>
              본 서비스는 개별 종목 매매를 권유하지 않습니다. 투자 판단과 그 결과는 이용자 본인의 책임입니다.
            </div>
          </>
        )}
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="between" style={{ marginBottom: 10 }}>
          <b style={{ fontSize: 14 }}>리플레이 트랙 예측</b>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {rp.length
              ? `등록 ${rp.length}건 · 검증 완료 ${rdone.length}건 · 적중 ${rdone.filter((p) => p.status === 'HIT').length}건`
              : '아직 리플레이 예측이 없습니다.'}
          </span>
        </div>
        {!rp.length && (
          <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
            아직 등록된 리플레이 예측이 없습니다. 위에서 등록하고 재생 버튼으로 시간을 진행해 보세요.
          </div>
        )}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 12, minWidth: 0 }}>
          {rp.slice(0, 6).map((p) => (
            <PredTile key={p.id} p={buildPredVM(e, p)} />
          ))}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,minmax(0,1fr))', gap: 16, marginBottom: 16, minWidth: 0 }}>
        <StatCard label="시즌 예수금" value={e.num(e.sCash)} />
        <StatCard label="평가금액" value={e.num(val)} />
        <StatCard label="시즌 수익률" value={e.pct(ret)} deltaDirection={ret >= 0 ? 'up' : 'down'} />
        <StatCard label="총 자산" value={e.num(total)} accent />
      </div>

      {e.sClosed && (
        <div className="card scroll-x" style={{ marginBottom: 16 }}>
          <b style={{ fontSize: 15 }}>🏁 시즌 종료 · 정답 공개</b>
          <div style={{ color: 'var(--text-secondary)', fontSize: 12.5, marginBottom: 10 }}>
            최종 자산 {e.num(e.sClosed.final)} PRT · 손익 {e.sClosed.profit >= 0 ? '+' : ''}
            {e.num(e.sClosed.profit)} PRT (지갑 반영 완료)
          </div>
          <table>
            <thead>
              <tr>
                <th>블라인드</th>
                <th>실제 종목</th>
                <th>구간 수익률</th>
              </tr>
            </thead>
            <tbody>
              {e.sClosed.board.map((x) => (
                <tr key={x.id}>
                  <td>
                    <b>{x.id}</b>
                  </td>
                  <td>{x.real}</td>
                  <td style={{ color: x.ret >= 0 ? 'var(--status-error)' : 'var(--accent)' }}>{e.pct(x.ret)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="card scroll-x" style={{ padding: 8 }}>
        <table style={{ minWidth: 760 }}>
          <thead>
            <tr>
              <th>종목</th>
              <th>섹터</th>
              <th>종가</th>
              <th>전일비</th>
              <th>보유</th>
              <th style={{ textAlign: 'right' }}>주문</th>
            </tr>
          </thead>
          <tbody>
            {e.TICK.map((t) => {
              const px = e.sPrice(t);
              const pv = t.series[Math.max(0, e.sDay - 1)];
              const ch = ((px - pv) / pv) * 100;
              return (
                <tr key={t.id}>
                  <td>
                    <b>{t.id}</b>
                  </td>
                  <td>{t.sector}</td>
                  <td>
                    <b>{e.num(px)}</b>
                  </td>
                  <td style={{ color: ch >= 0 ? 'var(--status-error)' : 'var(--accent)' }}>{e.pct(ch)}</td>
                  <td>{t.qty ? `${t.qty}주 · 평단 ${e.num(t.avg)}` : '-'}</td>
                  <td>
                    <div className="row" style={{ justifyContent: 'flex-end' }}>
                      <input
                        type="number"
                        min={1}
                        value={S.qtyMap[t.id] ?? 10}
                        onChange={(ev) => e.setState((s) => ({ qtyMap: { ...s.qtyMap, [t.id]: ev.target.value } }))}
                        style={{ width: 70 }}
                      />
                      <Button size="sm" variant="primary" onClick={() => e.sTrade(t.id, 1)}>
                        매수
                      </Button>
                      <Button size="sm" variant="secondary" onClick={() => e.sTrade(t.id, -1)}>
                        매도
                      </Button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </>
  );
}
