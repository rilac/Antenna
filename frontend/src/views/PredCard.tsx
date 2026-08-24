import type { PredVM } from '../engine/engine';
import { Badge, Button } from '../ui';

export function Avatar({ color, initial, size = 34 }: { color: string; initial: string; size?: number }) {
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: size / 3,
        background: color,
        color: '#fff',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontWeight: 800,
        fontSize: size * 0.4,
        flex: 'none',
      }}
    >
      {initial}
    </div>
  );
}

function Metric({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{label}</div>
      <b style={color ? { color } : undefined}>{value}</b>
    </div>
  );
}

function TrackTags({ p }: { p: PredVM }) {
  return (
    <>
      <span
        style={{
          fontSize: 10.5,
          fontWeight: 700,
          padding: '3px 9px',
          borderRadius: 999,
          color: p.trackColor,
          border: `1px solid ${p.trackColor}`,
        }}
      >
        {p.trackLabel}
      </span>
      <span
        style={{
          fontSize: 11,
          fontWeight: 700,
          padding: '3px 10px',
          borderRadius: 999,
          color: p.dirColor,
          background: p.dirBg,
        }}
      >
        {p.dirLabel}
      </span>
    </>
  );
}

const badgeBig = { fontSize: 13, padding: '6px 14px', fontWeight: 800 } as const;

/** 피드용 전체 카드 — 근거 잠금·커밋 해시 줄 포함 */
export function PredCard({ p }: { p: PredVM }) {
  return (
    <div className="card">
      <div className="between" style={{ marginBottom: 10, cursor: 'pointer' }} onClick={p.onOpenProfile}>
        <div className="row">
          <Avatar color={p.uColor} initial={p.uInitial} />
          <div>
            <b>{p.uName}</b>
            <div style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
              {p.uHandle} · {p.dayLabel} 등록
            </div>
          </div>
        </div>
        <Badge status={p.badgeStatus} style={badgeBig}>
          {p.badgeText}
        </Badge>
      </div>

      <div className="between" style={{ flexWrap: 'wrap', marginBottom: 14, rowGap: 4 }}>
        <div className="row" style={{ flexWrap: 'wrap' }}>
          <b style={{ fontSize: 15 }}>{p.stockName}</b>
          <span className="mono" style={{ fontSize: 11, color: 'var(--text-muted)' }}>
            {p.stockCode}
          </span>
          <Badge status="neutral">{p.sectorTag}</Badge>
          <TrackTags p={p} />
        </div>
        <div style={{ fontSize: 12, color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
          확신도 {p.confPct}% · {p.horizonLabel}
        </div>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4,minmax(0,1fr))',
          gap: 8,
          textAlign: 'center',
          marginBottom: 10,
          minWidth: 0,
        }}
      >
        <Metric label="직전 종가" value={p.refCloseFmt} />
        <div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>목표가</div>
          <b style={{ color: p.dirColor }}>{p.targetFmt}</b>
          <div style={{ fontSize: 11, color: p.dirColor }}>{p.gapFmt}</div>
        </div>
        <Metric label="기준가" value={p.entryFmt} />
        <Metric label={p.lastLabel} value={p.lastValueFmt} />
      </div>

      {p.noteLocked ? (
        <div
          style={{
            border: '1px dashed var(--border-strong)',
            borderRadius: 'var(--radius-md)',
            padding: 12,
            textAlign: 'center',
            background: 'var(--surface-sunken)',
          }}
        >
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>🔒 구독자에게만 공개되는 분석 근거입니다.</div>
          <div style={{ marginTop: 8 }}>
            <Button variant="accent" size="sm" onClick={p.onSubscribe} style={{ width: '100%' }}>
              {p.subLabel}
            </Button>
          </div>
        </div>
      ) : (
        <div style={{ background: 'var(--accent-softer)', borderRadius: 'var(--radius-md)', padding: 12, fontSize: 13 }}>
          <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--accent)' }}>🔓 구독자 전용 · 분석 근거</span>
          <br />
          {p.noteText}
        </div>
      )}

      <div className="mono" style={{ fontSize: 10.5, color: 'var(--text-muted)', marginTop: 10 }}>
        commit {p.commitShort}… · {p.anchorText}
        {p.revealText}
      </div>
    </div>
  );
}

/** 프로필 탭용 축약 카드 — 근거 영역 없음 */
export function PredCardCompact({ p }: { p: PredVM }) {
  return (
    <div className="card">
      <div className="between" style={{ marginBottom: 10 }}>
        <div className="row">
          <Avatar color={p.uColor} initial={p.uInitial} />
          <div>
            <b>{p.uName}</b>
            <div style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>{p.dayLabel} 등록</div>
          </div>
        </div>
        <Badge status={p.badgeStatus} style={badgeBig}>
          {p.badgeText}
        </Badge>
      </div>
      <div className="between" style={{ flexWrap: 'wrap', marginBottom: 10 }}>
        <div className="row" style={{ flexWrap: 'wrap' }}>
          <b style={{ fontSize: 15 }}>{p.stockName}</b>
          <span className="mono" style={{ fontSize: 11, color: 'var(--text-muted)' }}>
            {p.stockCode}
          </span>
          <TrackTags p={p} />
        </div>
        <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{p.horizonLabel}</div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,minmax(0,1fr))', gap: 8, textAlign: 'center', minWidth: 0 }}>
        <Metric label="직전 종가" value={p.refCloseFmt} />
        <Metric label="목표가" value={p.targetFmt} color={p.dirColor} />
        <Metric label="기준가" value={p.entryFmt} />
        <Metric label={p.lastLabel} value={p.lastValueFmt} />
      </div>
    </div>
  );
}

/** 리플레이 트랙 요약 타일 */
export function PredTile({ p }: { p: PredVM }) {
  return (
    <div style={{ border: '1px solid var(--divider)', borderRadius: 'var(--radius-md)', padding: 12 }}>
      <div className="between" style={{ marginBottom: 8 }}>
        <b style={{ fontSize: 14 }}>
          {p.stockName} {p.dirLabel}
        </b>
        <Badge status={p.badgeStatus}>{p.badgeText}</Badge>
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4,minmax(0,1fr))',
          gap: 6,
          textAlign: 'center',
          fontSize: 12,
          minWidth: 0,
        }}
      >
        <Metric label="등록일 종가" value={p.refCloseFmt} />
        <Metric label="목표가" value={p.targetFmt} color={p.dirColor} />
        <Metric label="기준가" value={p.entryFmt} />
        <Metric label={p.lastLabel} value={p.lastValueFmt} />
      </div>
      <div className="mono" style={{ fontSize: 10.5, color: 'var(--text-muted)', marginTop: 8 }}>
        commit {p.commitShort}… · {p.anchorText}
        {p.revealText}
      </div>
    </div>
  );
}
