// Date helpers.
//
// The backend sends plain calendar dates like "2026-11-01" (no time, no timezone).
// new Date("2026-11-01") treats that as 00:00 UTC, so in a timezone west of UTC (for example
// the US) toLocaleDateString shows 31 Oct, one day early. We build the date from its parts
// instead, so a calendar date always shows as the same calendar date for every viewer.

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/

/** "2026-11-01" -> a Date at local midnight of 1 Nov. Anything else goes through new Date(). */
export function parseDate(value) {
  if (value instanceof Date) return value
  const m = typeof value === 'string' ? DATE_ONLY.exec(value) : null
  if (m) return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  return new Date(value)
}

/** "01 Nov 2026", or an em dash for empty / invalid values. */
export function formatDate(value) {
  if (!value) return '—'
  const d = parseDate(value)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
}
