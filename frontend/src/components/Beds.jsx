// Bed occupancy as a row of small bed blocks: filled = taken, outlined = free.
// <Beds total={4} taken={3} />  for one room.
// <Beds free={5} />             when only the free count is known (search cards).
export default function Beds({ total, taken, free, max = 10 }) {
  if (total == null) {
    const shown = Math.min(Math.max(free ?? 0, 0), max)
    return (
      <span className="beds" role="img" aria-label={`${free} beds free`}>
        {Array.from({ length: max }).map((_, i) => <i key={i} className={i < shown ? 'free' : 'gone'} />)}
        {free > max && <b>+{free - max}</b>}
      </span>
    )
  }
  const cap = Math.min(total, 12)
  return (
    <span className="beds" role="img" aria-label={`${taken} of ${total} beds taken`}>
      {Array.from({ length: cap }).map((_, i) => <i key={i} className={i < taken ? 'taken' : 'free'} />)}
      {total > cap && <b>+{total - cap}</b>}
    </span>
  )
}
