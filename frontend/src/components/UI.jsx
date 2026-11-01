// Small reusable pieces used across many pages.
import { formatDate } from '../utils/dates'

export function PageHeader({ title, subtitle, children }) {
  return (
    <div className="page-head">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      <div className="actions">{children}</div>
    </div>
  )
}

export function StatCard({ label, value, tone = '' }) {
  return (
    <div className={`stat ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

// Coloured pill for statuses like PAID / OVERDUE / OPEN ...
export function Badge({ value }) {
  if (!value) return null
  return <span className={`badge b-${value}`}>{String(value).replace('_', ' ')}</span>
}

// A grey shimmering block that stands in for content while it loads.
export function Skeleton({ h = 16, w = '100%', r = 8, style }) {
  return <div className="skel" style={{ height: h, width: w, borderRadius: r, ...style }} aria-hidden="true" />
}

export function SkeletonGrid({ n = 6 }) {
  return (
    <div className="pg-grid" role="status" aria-label="Loading">
      {Array.from({ length: n }).map((_, i) => (
        <div key={i}>
          <Skeleton h="auto" r={16} style={{ aspectRatio: '4 / 3' }} />
          <Skeleton h={16} w="70%" style={{ marginTop: 12 }} />
          <Skeleton h={12} w="45%" style={{ marginTop: 8 }} />
          <Skeleton h={14} w="30%" style={{ marginTop: 10 }} />
        </div>
      ))}
    </div>
  )
}

// Default loading state for every page: a title bar and a few cards.
export function Loader() {
  return (
    <div className="loader-skel" role="status" aria-label="Loading">
      <Skeleton h={30} w={220} />
      <Skeleton h={14} w={320} style={{ marginTop: 10 }} />
      {[0, 1, 2].map((i) => <Skeleton key={i} h={86} r={14} style={{ marginTop: 18 }} />)}
    </div>
  )
}

export function Empty({ text }) {
  return <div className="empty">{text}</div>
}

export function Field({ label, children }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  )
}

export const money = (n) => '₹' + Number(n || 0).toLocaleString('en-IN')
export const date = formatDate // date-only strings are parsed as local dates (see utils/dates.js)
export const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
