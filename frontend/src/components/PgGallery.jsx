import { useEffect, useState, useCallback } from 'react'
import { BuildingIcon } from './Icons'

const src = (id) => `/api/public/pg-photos/${id}`

// Photo grid on top of the PG page + a full-screen viewer (arrow keys, Esc, swipe buttons).
// With no photos it says so plainly.
export default function PgGallery({ pg }) {
  const ids = pg.photoIds || []
  const [open, setOpen] = useState(null) // index of the photo shown large

  const move = useCallback((d) => setOpen((i) => (i === null ? i : (i + d + ids.length) % ids.length)), [ids.length])

  useEffect(() => {
    if (open === null) return
    const onKey = (e) => {
      if (e.key === 'Escape') setOpen(null)
      if (e.key === 'ArrowRight') move(1)
      if (e.key === 'ArrowLeft') move(-1)
    }
    window.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => { window.removeEventListener('keydown', onKey); document.body.style.overflow = '' }
  }, [open, move])

  if (ids.length === 0) {
    return (
      <div className="detail-nophoto">
        <BuildingIcon width={36} height={36} />
        <p><strong>No photos yet</strong></p>
        <p className="muted small">Ask the owner for photos when you call or visit.</p>
      </div>
    )
  }

  const shown = ids.slice(0, 5)
  return (
    <>
      <div className={`gallery g${Math.min(shown.length, 5)}`}>
        {shown.map((id, i) => (
          <button key={id} className="gallery-cell" onClick={() => setOpen(i)} aria-label={`Open photo ${i + 1}`}>
            <img src={src(id)} alt={`${pg.name} photo ${i + 1}`} loading={i === 0 ? 'eager' : 'lazy'} />
            {i === shown.length - 1 && ids.length > shown.length && <span className="gallery-more">+{ids.length - shown.length} more</span>}
          </button>
        ))}
      </div>

      {open !== null && (
        <div className="lightbox" onMouseDown={() => setOpen(null)} role="dialog" aria-modal="true">
          <button className="lb-close" onClick={() => setOpen(null)} aria-label="Close">×</button>
          {ids.length > 1 && <button className="lb-nav prev" onMouseDown={(e) => e.stopPropagation()} onClick={() => move(-1)} aria-label="Previous">‹</button>}
          <img src={src(ids[open])} alt={`${pg.name} photo ${open + 1}`} onMouseDown={(e) => e.stopPropagation()} />
          {ids.length > 1 && <button className="lb-nav next" onMouseDown={(e) => e.stopPropagation()} onClick={() => move(1)} aria-label="Next">›</button>}
          <div className="lb-count">{open + 1} / {ids.length}</div>
        </div>
      )}
    </>
  )
}
