import { MONTHS } from './UI'

const short = (n) => {
  n = Number(n || 0)
  if (n >= 100000) return `₹${(n / 100000).toFixed(1).replace(/\.0$/, '')}L`
  if (n >= 1000) return `₹${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`
  return `₹${n}`
}

/** Collected rent per month, as bars. Pale bar = rent due that month, sand bar = rent paid. */
export function RevenueBars({ points }) {
  const max = Math.max(1, ...points.map((p) => Number(p.expected) || 0), ...points.map((p) => Number(p.collected) || 0))
  const last = points.length - 1
  return (
    <>
      <div className="bars" role="img" aria-label={'Rent collected: ' + points.map((p) => `${MONTHS[p.month - 1]} ${short(p.collected)}`).join(', ')}>
        {points.map((p, i) => (
          <div key={`${p.year}-${p.month}`} className={`bar-col ${i === last ? 'now' : ''}`}>
            <span className="bar-val">{Number(p.collected) > 0 ? short(p.collected) : ''}</span>
            <div className="bar" style={{ height: `${Math.max(2, ((Number(p.expected) || 0) / max) * 100)}%` }}>
              <i style={{ height: `${(Math.min(Number(p.collected) || 0, Number(p.expected) || 0) / Math.max(1, Number(p.expected) || 1)) * 100}%` }} />
            </div>
            <label>{MONTHS[p.month - 1]}</label>
          </div>
        ))}
      </div>
      <div className="legend">
        <span><i style={{ background: 'var(--sand)' }} />Collected</span>
        <span><i style={{ background: 'rgba(255,255,255,0.3)' }} />Due that month</span>
      </div>
    </>
  )
}

/** Occupancy ring: sage = beds taken. */
export function Donut({ percent, label, sub }) {
  const r = 52, c = 2 * Math.PI * r
  return (
    <div className="donut-label">
      <svg viewBox="0 0 132 132" aria-hidden="true">
        <circle cx="66" cy="66" r={r} fill="none" stroke="#E6DDCF" strokeWidth="14" />
        <circle cx="66" cy="66" r={r} fill="none" stroke="#0F766E" strokeWidth="14" strokeLinecap="butt"
          strokeDasharray={`${(c * percent) / 100} ${c}`} transform="rotate(-90 66 66)" />
      </svg>
      <div><strong>{label}</strong><span>{sub}</span></div>
    </div>
  )
}
