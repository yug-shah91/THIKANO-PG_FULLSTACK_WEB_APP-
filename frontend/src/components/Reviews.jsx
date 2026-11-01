import { useEffect, useState } from 'react'
import { publicApi, reviewApi } from '../api/services'
import { errMsg } from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { Stars } from './Stars'
import ReviewModal from './ReviewModal'
import { Skeleton, date } from './UI'

// Rating summary + list of reviews on the PG page.
export default function Reviews({ pg }) {
  const { user } = useAuth()
  const [summary, setSummary] = useState(null)
  const [items, setItems] = useState(null)
  const [page, setPage] = useState(0)
  const [more, setMore] = useState(false)
  const [error, setError] = useState('')
  const [mine, setMine] = useState(null) // my review status (tenants only)
  const [writing, setWriting] = useState(false)

  const loadAll = () => {
    publicApi.reviewSummary(pg.id).then((r) => setSummary(r.data)).catch((e) => setError(errMsg(e)))
    publicApi.reviews(pg.id, 0).then((r) => { setItems(r.data.content); setPage(0); setMore(!r.data.last) }).catch((e) => { setError(errMsg(e)); setItems([]) })
    if (user?.role === 'TENANT') reviewApi.status().then((r) => setMine(r.data)).catch(() => setMine(null))
  }
  useEffect(loadAll, [pg.id, user])

  const loadMore = () => publicApi.reviews(pg.id, page + 1).then((r) => {
    setItems((cur) => [...cur, ...r.data.content]); setPage(page + 1); setMore(!r.data.last)
  }).catch((e) => setError(errMsg(e)))

  const stayedHere = mine && mine.pgId === pg.id
  const total = summary?.count || 0

  return (
    <div>
      {error && <p className="error-text">{error}</p>}
      {!summary ? <Skeleton h={90} r={14} /> : total === 0 ? (
        <p className="muted">No reviews yet. {stayedHere && mine.eligible ? 'Be the first to review.' : 'Only people who stayed here can review.'}</p>
      ) : (
        <div className="rev-summary">
          <div className="rev-avg">
            <div className="rev-big">{summary.average.toFixed(1)}</div>
            <Stars value={summary.average} size={18} />
            <div className="muted small">{total} review{total > 1 ? 's' : ''}</div>
          </div>
          <div className="rev-bars">
            {[5, 4, 3, 2, 1].map((n) => (
              <div key={n} className="rev-bar">
                <span>{n}★</span>
                <div><i style={{ width: `${(summary.distribution[n] / total) * 100}%` }} /></div>
                <span className="muted">{summary.distribution[n]}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {stayedHere && (
        <div style={{ margin: '14px 0' }}>
          {mine.eligible
            ? <button className="btn btn-dark btn-sm" onClick={() => setWriting(true)}>{mine.review ? 'Edit your review' : 'Write a review'}</button>
            : <p className="muted small">{mine.reason}</p>}
        </div>
      )}

      {items && items.length > 0 && (
        <div className="rev-list">
          {items.map((r) => (
            <article key={r.id} className="rev">
              <div className="rev-head">
                <span className="avatar">{r.reviewerName[0]}</span>
                <div>
                  <strong>{r.reviewerName}</strong>
                  <div className="muted small">{date(r.createdAt)} · <Stars value={r.rating} size={13} /></div>
                </div>
              </div>
              {r.comment && <p>{r.comment}</p>}
              {r.ownerReply && (
                <div className="rev-reply"><strong>Reply from the owner</strong><p>{r.ownerReply}</p></div>
              )}
            </article>
          ))}
          {more && <button className="btn btn-ghost btn-sm" onClick={loadMore}>Show more reviews</button>}
        </div>
      )}

      {writing && (
        <ReviewModal pgName={pg.name} existing={mine?.review} onClose={() => setWriting(false)}
          onSaved={() => { setWriting(false); loadAll() }} />
      )}
    </div>
  )
}
