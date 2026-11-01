// Read-only stars, can show partial values like 4.3. Pure CSS, no images.
export function Stars({ value = 0, size = 16 }) {
  const pct = Math.max(0, Math.min(5, value)) / 5 * 100
  return (
    <span className="stars" style={{ fontSize: size }} role="img" aria-label={`${value} out of 5 stars`}>
      <span className="stars-bg">★★★★★</span>
      <span className="stars-fg" style={{ width: `${pct}%` }}>★★★★★</span>
    </span>
  )
}

// Clickable stars for writing a review.
export function StarPicker({ value, onChange }) {
  return (
    <div className="star-picker" role="radiogroup" aria-label="Rating">
      {[1, 2, 3, 4, 5].map((n) => (
        <button type="button" key={n} role="radio" aria-checked={value === n} aria-label={`${n} star${n > 1 ? 's' : ''}`}
          className={n <= value ? 'on' : ''} onClick={() => onChange(n)}>★</button>
      ))}
    </div>
  )
}

export function Rating({ avg, count }) {
  if (!count) return <span className="muted small">New</span>
  return <span className="rating"><span className="rating-star">★</span> {avg.toFixed(1)} <span className="muted">({count})</span></span>
}
