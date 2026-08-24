import { useEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import type { BadgeStatus } from '../engine/types';

/** 프로토타입이 쓰던 Qurie 디자인 시스템 컴포넌트를 필요한 것만 옮긴 것 */

type Variant = 'primary' | 'secondary' | 'ghost' | 'accent';

export function Button({
  variant = 'primary',
  size = 'md',
  disabled = false,
  children,
  onClick,
  style,
}: {
  variant?: Variant;
  size?: 'sm' | 'md';
  disabled?: boolean;
  children?: ReactNode;
  onClick?: () => void;
  style?: CSSProperties;
}) {
  const [hover, setHover] = useState(false);
  const sizes: Record<string, CSSProperties> = {
    sm: { fontSize: 13, padding: '7px 14px', minHeight: 'var(--control-h-sm)' },
    md: { fontSize: 14, padding: '10px 18px', minHeight: 'var(--control-h-md)' },
  };
  const variants: Record<Variant, CSSProperties> = {
    primary: { background: 'var(--ink)', color: 'var(--text-inverse)' },
    secondary: { background: 'var(--surface-card)', color: 'var(--ink)', border: '1px solid var(--border-strong)' },
    ghost: { background: 'transparent', color: 'var(--text-secondary)' },
    accent: { background: 'var(--accent)', color: 'var(--text-inverse)' },
  };
  const hovers: Record<Variant, CSSProperties> = {
    primary: { background: 'var(--grey-600)' },
    secondary: { background: 'var(--surface-hover)' },
    ghost: { background: 'var(--surface-hover)', color: 'var(--ink)' },
    accent: { background: 'var(--accent-strong)' },
  };
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        fontFamily: 'var(--font-sans)',
        fontWeight: 600,
        borderRadius: 'var(--radius-control)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
        border: '1px solid transparent',
        transition: 'background 140ms ease-out,border-color 140ms ease-out',
        lineHeight: 1,
        whiteSpace: 'nowrap',
        ...sizes[size],
        ...variants[variant],
        ...(hover && !disabled ? hovers[variant] : {}),
        ...(disabled ? { opacity: 0.45, pointerEvents: 'none' } : {}),
        ...style,
      }}
    >
      {children}
    </button>
  );
}

export function Badge({
  status = 'neutral',
  children,
  style,
}: {
  status?: BadgeStatus;
  children?: ReactNode;
  style?: CSSProperties;
}) {
  const map: Record<BadgeStatus, [string, string]> = {
    success: ['var(--status-success)', 'var(--status-success-bg)'],
    warning: ['var(--status-warning)', 'var(--status-warning-bg)'],
    error: ['var(--status-error)', 'var(--status-error-bg)'],
    neutral: ['var(--status-neutral)', 'var(--status-neutral-bg)'],
    accent: ['var(--status-accent)', 'var(--status-accent-bg)'],
  };
  const [fg, bg] = map[status] ?? map.neutral;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 5,
        background: bg,
        color: fg,
        borderRadius: 'var(--radius-pill)',
        padding: '3px 10px',
        fontSize: 11,
        fontWeight: 600,
        whiteSpace: 'nowrap',
        ...style,
      }}
    >
      {children}
    </span>
  );
}

export interface Option {
  value: string;
  label: string;
}

export function Select({
  options,
  value,
  onChange,
  style,
}: {
  options: Option[];
  value: string;
  onChange: (v: string) => void;
  style?: CSSProperties;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);
  const cur = options.find((o) => o.value === value) ?? options[0];
  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-block', ...style }}>
      <button
        onClick={() => setOpen((o) => !o)}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          width: '100%',
          gap: 6,
          background: 'var(--surface-card)',
          border: '1px solid var(--border-strong)',
          borderRadius: 'var(--radius-control)',
          padding: '8px 16px',
          fontFamily: 'var(--font-sans)',
          fontSize: 14,
          fontWeight: 500,
          color: 'var(--ink)',
          cursor: 'pointer',
        }}
      >
        {cur?.label}
        <span style={{ color: 'var(--text-muted)', fontSize: 10, transform: 'rotate(90deg)', fontWeight: 600 }}>
          &gt;
        </span>
      </button>
      {open && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(100% + 4px)',
            left: 0,
            minWidth: '100%',
            background: 'var(--surface-card)',
            border: '1px solid var(--border-strong)',
            borderRadius: 'var(--radius-md)',
            boxShadow: 'var(--shadow-popover)',
            backdropFilter: 'blur(18px)',
            padding: 5,
            zIndex: 30,
            maxHeight: 260,
            overflowY: 'auto',
          }}
        >
          {options.map((o) => {
            const sel = o.value === value;
            return (
              <div
                key={o.value}
                onClick={() => {
                  setOpen(false);
                  onChange(o.value);
                }}
                style={{
                  padding: '6px 12px',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: 13,
                  fontWeight: sel ? 600 : 400,
                  color: sel ? 'var(--accent)' : 'var(--ink)',
                  background: sel ? 'var(--accent-softer)' : 'transparent',
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                }}
              >
                {o.label}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export function StatCard({
  label,
  value,
  deltaDirection,
  accent = false,
}: {
  label: string;
  value: string;
  deltaDirection?: 'up' | 'down';
  accent?: boolean;
}) {
  return (
    <div
      style={{
        background: 'var(--surface-card)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--card-radius)',
        boxShadow: 'var(--shadow-card)',
        padding: 'var(--stat-card-padding)',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        minWidth: 0,
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span
          style={{
            fontSize: 'var(--text-kpi)',
            fontWeight: 700,
            color: deltaDirection
              ? deltaDirection === 'up'
                ? 'var(--status-success)'
                : 'var(--status-error)'
              : accent
                ? 'var(--accent)'
                : 'var(--ink)',
            letterSpacing: '-0.02em',
            lineHeight: 1.1,
            fontVariantNumeric: 'tabular-nums',
            overflowWrap: 'anywhere',
          }}
        >
          {value}
        </span>
        <span style={{ fontSize: 12, color: 'var(--text-secondary)', fontWeight: 500 }}>{label}</span>
      </div>
    </div>
  );
}

export interface Column<R> {
  key: string;
  label: string;
  align?: 'left' | 'right';
  width?: number;
  sortable?: boolean;
  render?: (r: R) => ReactNode;
}

export function DataTable<R extends Record<string, unknown>>({
  columns,
  rows,
  rowKey,
}: {
  columns: Column<R>[];
  rows: R[];
  rowKey: string;
}) {
  const [sort, setSort] = useState<{ key: string; dir: 'asc' | 'desc' } | null>(null);
  const sorted = useMemo(() => {
    if (!sort) return rows;
    return [...rows].sort((a, b) => {
      const av = a[sort.key] as number | string;
      const bv = b[sort.key] as number | string;
      return (av > bv ? 1 : av < bv ? -1 : 0) * (sort.dir === 'asc' ? 1 : -1);
    });
  }, [rows, sort]);
  const th: CSSProperties = {
    fontSize: 11,
    fontWeight: 600,
    letterSpacing: 'var(--ls-caps)',
    textTransform: 'uppercase',
    color: 'var(--text-secondary)',
    padding: '10px 16px',
    borderBottom: '1px solid var(--border-strong)',
    whiteSpace: 'nowrap',
    userSelect: 'none',
  };
  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14, minWidth: 720 }}>
      <thead>
        <tr>
          {columns.map((c) => (
            <th
              key={c.key}
              onClick={() =>
                c.sortable &&
                setSort((s) =>
                  s && s.key === c.key ? { key: c.key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key: c.key, dir: 'asc' },
                )
              }
              style={{
                ...th,
                textAlign: c.align ?? 'left',
                width: c.width,
                cursor: c.sortable ? 'pointer' : 'default',
                color: sort && sort.key === c.key ? 'var(--accent)' : th.color,
              }}
            >
              {c.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {sorted.map((r, i) => (
          <tr
            key={String(r[rowKey] ?? i)}
            onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--surface-hover)')}
            onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
          >
            {columns.map((c) => (
              <td
                key={c.key}
                style={{
                  padding: 'var(--table-cell-pad)',
                  borderBottom: '1px solid var(--divider)',
                  color: 'var(--text-body)',
                  textAlign: c.align ?? 'left',
                  verticalAlign: 'middle',
                }}
              >
                {c.render ? c.render(r) : (r[c.key] as ReactNode)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function Modal({
  open,
  title,
  description,
  children,
  primaryLabel,
  secondaryLabel,
  onPrimary,
  onSecondary,
  onClose,
  width = 480,
}: {
  open: boolean;
  title: string;
  description?: string;
  children?: ReactNode;
  primaryLabel?: string;
  secondaryLabel?: string;
  onPrimary?: () => void;
  onSecondary?: () => void;
  onClose?: () => void;
  width?: number;
}) {
  if (!open) return null;
  const btn: CSSProperties = {
    borderRadius: 'var(--radius-control)',
    padding: '10px 18px',
    fontSize: 14,
    fontWeight: 600,
    cursor: 'pointer',
  };
  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(6,6,10,0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 100,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'rgba(24,20,32,0.96)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-xl)',
          boxShadow: 'var(--shadow-modal)',
          width,
          maxWidth: 'calc(100vw - 48px)',
          padding: 28,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 600, color: 'var(--ink)' }}>{title}</h3>
            {onClose && (
              <button
                onClick={onClose}
                aria-label="닫기"
                style={{
                  border: 'none',
                  background: 'transparent',
                  color: 'var(--text-muted)',
                  fontSize: 18,
                  cursor: 'pointer',
                  lineHeight: 1,
                  padding: 4,
                }}
              >
                ×
              </button>
            )}
          </div>
          {description && (
            <p style={{ margin: 0, fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.55 }}>{description}</p>
          )}
        </div>
        {children}
        {(primaryLabel || secondaryLabel) && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
            {secondaryLabel && (
              <button
                onClick={onSecondary}
                style={{ ...btn, background: 'var(--surface-card)', color: 'var(--ink)', border: '1px solid var(--border-strong)' }}
              >
                {secondaryLabel}
              </button>
            )}
            {primaryLabel && (
              <button
                onClick={onPrimary}
                style={{ ...btn, background: 'var(--ink)', color: 'var(--text-inverse)', border: '1px solid transparent' }}
              >
                {primaryLabel}
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
