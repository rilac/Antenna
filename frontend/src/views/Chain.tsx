import type { Engine } from '../engine/engine';
import type { BadgeStatus, BlockType } from '../engine/types';
import { Badge, Button } from '../ui';

const BTYPE: Record<BlockType, string> = {
  GENESIS: '제네시스',
  PREDICT: '예측 커밋',
  RESULT: '검증 결과',
  ANCHOR: '⛓ 머클 앵커',
  SUBSCRIBE: '구독 결제',
  SEASON: '시즌 정산',
  SEASON_ENTRY: '시즌 참가비',
  SEASON_REWARD: '시즌 상금',
  SLOT_FEE: '등록 슬롯 초과 결제',
  BACKTEST: '백테스트 실행',
  CERT: '검증 증명서 발급',
};

export function Chain({ e }: { e: Engine }) {
  const S = e.state;
  const v = e.audit();
  const bad = v.some((x) => !x.valid);
  const list = e.chain.slice().reverse().slice(0, S.chainMore);

  return (
    <>
      <h2 style={{ margin: '0 0 4px' }}>커밋 원장</h2>
      <p style={{ color: 'var(--text-secondary)', fontSize: 13, margin: '0 0 16px' }}>
        예측 원문은 DB에, 해시만 원장에 남습니다. 커밋은 배치마다 머클루트 1건으로 묶여 앵커링됩니다.
      </p>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="between">
          <div>
            <b style={{ fontSize: 15 }}>{bad ? '⚠ 무결성 훼손 감지' : '✅ 무결성 정상'}</b>
            <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              블록 {e.chain.length}개 · 앵커 {e.chain.filter((b) => b.type === 'ANCHOR').length}건 · 앵커 대기{' '}
              {e.pending.length}건
            </div>
          </div>
          {bad && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                e.chain.forEach((b) => {
                  if (b._orig) {
                    b.data = b._orig;
                    delete b._orig;
                  }
                });
                e.toast('원본 데이터로 복구했습니다.');
              }}
            >
              원본 복구
            </Button>
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gap: 10 }}>
        {list.map((b) => {
          const st = v[b.i];
          const status: BadgeStatus = !st.okSelf
            ? 'error'
            : !st.rootOk
              ? 'error'
              : !st.okLink
                ? 'warning'
                : !st.valid
                  ? 'warning'
                  : 'success';
          const statusLabel = !st.okSelf
            ? '✘ 해시 불일치'
            : !st.rootOk
              ? '✘ 머클루트 불일치'
              : !st.okLink
                ? '⚠ 연결 끊김'
                : !st.valid
                  ? '⚠ 검증 불가'
                  : '✔ 검증됨';
          const dataLine = Object.keys(b.data)
            .map((k) => k + ' ' + (typeof b.data[k] === 'number' ? e.num(b.data[k] as number) : b.data[k]))
            .join(' · ');

          return (
            <div key={b.i} className="card" style={{ padding: '14px 16px' }}>
              <div className="between" style={{ flexWrap: 'wrap' }}>
                <div className="row">
                  <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>#{b.i}</span>
                  <b style={{ fontSize: 13 }}>{BTYPE[b.type] ?? b.type}</b>
                  <span className="mono" style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                    {b.ts}
                  </span>
                </div>
                <div className="row" style={{ flexWrap: 'wrap' }}>
                  <Badge status={status}>{statusLabel}</Badge>
                  {b.type === 'PREDICT' && (
                    <>
                      <Badge status={b.anchor ? 'neutral' : 'warning'}>
                        {b.anchor ? '앵커 #' + b.anchor.idx : '앵커 대기'}
                      </Badge>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => {
                          b._orig = JSON.parse(JSON.stringify(b.data));
                          b.data.커밋해시 = e.hash('조작된목표가').slice(0, 24) + '…';
                          e.toast('블록 #' + b.i + '의 해시를 바꿔치기했습니다.', 'warn');
                        }}
                      >
                        목표가 변조 시도
                      </Button>
                    </>
                  )}
                  {b._orig && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        if (b._orig) {
                          b.data = b._orig;
                          delete b._orig;
                        }
                        e.bump();
                      }}
                    >
                      원복
                    </Button>
                  )}
                </div>
              </div>
              <div className="mono" style={{ fontSize: 11.5, color: 'var(--text-secondary)', margin: '8px 0' }}>
                {dataLine}
              </div>
              <div className="mono" style={{ fontSize: 10.5, color: 'var(--text-muted)', wordBreak: 'break-all' }}>
                prev {b.prev}
              </div>
              <div className="mono" style={{ fontSize: 10.5, color: 'var(--accent)', wordBreak: 'break-all' }}>
                hash {b.hash}
              </div>
            </div>
          );
        })}
      </div>

      {S.chainMore < e.chain.length && (
        <div style={{ marginTop: 12 }}>
          <Button
            variant="secondary"
            onClick={() => e.setState({ chainMore: S.chainMore + 20 })}
            style={{ width: '100%' }}
          >
            이전 블록 더보기 ({e.chain.length - S.chainMore}개 남음)
          </Button>
        </div>
      )}
    </>
  );
}
