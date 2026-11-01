import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { tenantApi, reviewApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import { PageHeader, Loader, Badge, date } from '../../components/UI'
import ReviewModal from '../../components/ReviewModal'
import { Stars } from '../../components/Stars'

export default function Profile() {
  const [t, setT] = useState(null)
  const [noStay, setNoStay] = useState(false) // account exists but nothing is booked yet
  const toast = useToast()
  const [rev, setRev] = useState(null)
  const [writing, setWriting] = useState(false)
  const loadReview = () => reviewApi.status().then((r) => setRev(r.data)).catch(() => {})

  useEffect(() => {
    loadReview()
    tenantApi.myProfile()
      .then((r) => setT(r.data))
      .catch((e) => {
        if (e.response?.status === 404) setNoStay(true)
        else toast(errMsg(e), 'error')
      })
  }, [])

  if (noStay) {
    return (
      <>
        <PageHeader title="My stay" />
        <div className="empty">
          <p><strong>You have not booked a room yet.</strong></p>
          <p>Browse PGs, pick a room with a free bed and book it in a minute.</p>
          <Link className="btn" to="/pgs">Find a PG</Link>
        </div>
      </>
    )
  }
  if (!t) return <Loader />

  const rows = [
    ['Name', t.name], ['Email', t.email], ['Phone', t.phone || '—'],
    ['PG', t.pgName], ['Room', t.roomNumber || 'Not allocated yet'],
    ['Move-in date', date(t.joinDate)], ['Expected leave', date(t.expectedLeaveDate)],
    ['Emergency contact', t.emergencyContactName ? `${t.emergencyContactName} (${t.emergencyContactPhone || '—'})` : '—'],
    ['ID proof', t.idProofType || '—'],
  ]

  return (
    <>
      <PageHeader title={`Hi, ${t.name}`} subtitle="Your stay details">
        <Link className="btn btn-ghost" to={`/pgs/${t.pgId}`}>PG details and owner contact</Link>
      </PageHeader>
      {!t.isActive && (
        <div className="card" style={{ marginBottom: 18 }}>
          <p><strong>This stay has ended.</strong> You can still see your details, rent history and the room report here.</p>
          <Link className="btn btn-sm" to="/pgs">Find a new PG</Link>
        </div>
      )}
      <div className="card">
        <div className="row-between"><h3>Booking</h3><Badge value={t.isActive ? 'ACTIVE' : 'LEFT'} /></div>
        <dl className="details">
          {rows.map(([k, v]) => (<div key={k}><dt>{k}</dt><dd>{v}</dd></div>))}
        </dl>
      </div>

      {rev && (
        <div className="card" style={{ marginTop: 18 }}>
          <h3>Rate your stay</h3>
          {rev.review ? (
            <p><Stars value={rev.review.rating} size={18} /> <span className="muted small">your review is public</span></p>
          ) : (
            <p className="muted">{rev.eligible ? 'Tell other students what living here is really like.' : rev.reason}</p>
          )}
          {rev.eligible && (
            <button className="btn btn-dark btn-sm" onClick={() => setWriting(true)}>{rev.review ? 'Edit review' : 'Write a review'}</button>
          )}
        </div>
      )}
      {writing && <ReviewModal pgName={rev.pgName} existing={rev.review} onClose={() => setWriting(false)} onSaved={() => { setWriting(false); loadReview() }} />}
    </>
  )
}
