import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { publicApi, bookingApi, visitApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import PgGallery from '../../components/PgGallery'
import PgMap from '../../components/PgMap'
import Reviews from '../../components/Reviews'
import { Rating } from '../../components/Stars'
import { Skeleton } from '../../components/UI'
import Beds from '../../components/Beds'
import CostCalculator from '../../components/CostCalculator'
import VisitPicker from '../../components/VisitPicker'
import { PinIcon } from '../../components/Icons'
import { Field, Loader, Badge, money } from '../../components/UI'

const iso = (d) => d.toLocaleDateString('en-CA') // YYYY-MM-DD in the user's own timezone
const label = (t) => t[0] + t.slice(1).toLowerCase()

export default function PgDetail() {
  const { id } = useParams()
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()

  const [pg, setPg] = useState(null)
  const [error, setError] = useState('')
  const [booking, setBooking] = useState(null) // the room being booked
  const [form, setForm] = useState({ moveInDate: iso(new Date()), emergencyContactName: '', emergencyContactPhone: '' })
  const [saving, setSaving] = useState(false)
  const [visiting, setVisiting] = useState(false)
  const [visit, setVisit] = useState(null) // { visitDate, visitTime }
  const [visitNote, setVisitNote] = useState('')

  const load = () => {
    setError('')
    publicApi.detail(id).then((r) => setPg(r.data)).catch((e) => setError(errMsg(e)))
  }
  useEffect(load, [id, user])

  if (error) {
    return (
      <div className="empty" style={{ marginTop: 40 }}>
        <p className="error-text">{error}</p>
        <Link className="btn btn-ghost" to="/pgs">Back to all PGs</Link>
      </div>
    )
  }
  if (!pg) {
    return (
      <div style={{ paddingTop: 28 }}>
        <Skeleton h={34} w={320} />
        <Skeleton h={14} w={220} style={{ marginTop: 10 }} />
        <Skeleton h={340} r={20} style={{ marginTop: 20 }} />
        <Skeleton h={120} r={14} style={{ marginTop: 24 }} />
      </div>
    )
  }

  const today = new Date()
  const maxDate = new Date(); maxDate.setDate(today.getDate() + 60)
  const isTenant = user?.role === 'TENANT'
  const onRequest = pg.bookingMode === 'ON_REQUEST'

  const startBooking = (room) => {
    if (!user) {
      toast('Log in or sign up to book a room.')
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    setBooking(room)
  }

  const startVisit = () => {
    if (!user) {
      toast('Log in or sign up to schedule a visit.')
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    if (!isTenant) { toast('Only tenant accounts can schedule visits.', 'error'); return }
    setVisit(null); setVisitNote(''); setVisiting(true)
  }

  const sendVisit = async (e) => {
    e.preventDefault()
    if (saving || !visit) return
    setSaving(true)
    try {
      await visitApi.request({ pgId: pg.id, ...visit, note: visitNote || null })
      toast('Visit requested. The owner will confirm it.')
      setVisiting(false)
      navigate('/tenant/visits')
    } catch (err) {
      toast(errMsg(err), 'error')
    } finally {
      setSaving(false)
    }
  }

  const confirm = async (e) => {
    e.preventDefault()
    if (saving) return
    setSaving(true)
    try {
      const body = {
        roomId: booking.id,
        moveInDate: form.moveInDate || null,
        emergencyContactName: form.emergencyContactName || null,
        emergencyContactPhone: form.emergencyContactPhone || null,
      }
      if (onRequest) {
        await bookingApi.request(body) // the bed is held while the owner decides
        toast('Request sent. The bed is held for you for 48 hours.')
        navigate('/tenant/booking')
      } else {
        await bookingApi.book(body)
        toast(`Room ${booking.roomNumber} booked at ${pg.name}`)
        navigate('/tenant/profile')
      }
    } catch (err) {
      toast(errMsg(err), 'error')
      setBooking(null)
      load() // the room may have just been taken: show the real availability
    } finally {
      setSaving(false)
    }
  }

  const mapUrl = pg.latitude != null && pg.longitude != null
    ? `https://www.google.com/maps?q=${pg.latitude},${pg.longitude}`
    : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${pg.address}, ${pg.city}`)}`

  return (
    <>
      <div className="detail-top">
        <Link to="/pgs" className="muted small">← All PGs</Link>
        <h1 style={{ marginTop: 10 }}>{pg.name}</h1>
        <p className="muted">{pg.address}, {pg.city} · <Rating avg={pg.ratingAvg} count={pg.reviewCount} /></p>
      </div>

      <PgGallery pg={pg} />

      <div className="detail-cols">
        <div>
          <section className="detail-section">
            <h2>About this PG</h2>
            <p>{pg.description || 'The owner has not added a description yet. Contact them for details.'}</p>
            <p className="muted">Managed by {pg.ownerName}</p>
          </section>

          <section className="detail-section">
            <h2>Rent rules</h2>
            <div className="rules">
              <div className="rule"><span className="muted small">Rent is due on</span><strong>Day {pg.rentDueDay} of each month</strong></div>
              <div className="rule"><span className="muted small">Late payment</span><strong>{pg.penaltyPercent}% extra, once</strong></div>
            </div>
          </section>

          {pg.latitude != null && pg.longitude != null && (
            <section className="detail-section">
              <h2>Where it is</h2>
              <PgMap pgs={[pg]} height={280} />
            </section>
          )}

          <section className="detail-section">
            <h2>Reviews</h2>
            <Reviews pg={pg} />
          </section>

          <section className="detail-section">
            <h2>True monthly cost</h2>
            <p className="muted small">The advertised rent is only part of it. Tick what you would use and see the real number.</p>
            <CostCalculator pg={pg} />
          </section>

          <section className="detail-section">
            <h2>Rooms</h2>
            {pg.rooms.length === 0 && <p className="muted">No rooms listed yet.</p>}
            {pg.rooms.map((r) => (
              <div className="room-row" key={r.id}>
                <div>
                  <h3>
                    Room {r.roomNumber}
                    <Badge value={r.bookable ? 'free' : 'full'} />
                  </h3>
                  <p className="muted small">
                    {label(r.type)} · Floor {r.floorNumber ?? '—'} · {r.availableBeds} of {r.capacity} beds free
                  </p>
                  {r.amenities && <p className="small">{r.amenities}</p>}
                </div>
                <div className="price">
                  <strong>{money(r.monthlyRent)}</strong>
                  <div className="muted small">per month</div>
                  {r.bookable ? (
                    (!user || isTenant) ? (
                      <button className="btn btn-sm" style={{ marginTop: 8 }} onClick={() => startBooking(r)}>
                        {!user ? 'Log in to book' : onRequest ? 'Request this room' : 'Book this room'}
                      </button>
                    ) : null
                  ) : (
                    <button className="btn btn-sm btn-ghost" style={{ marginTop: 8 }} disabled>No beds free</button>
                  )}
                </div>
              </div>
            ))}
            {user && !isTenant && (
              <p className="hint">You are logged in as {user.role.replace('_', ' ').toLowerCase()}. Only tenant accounts can book rooms.</p>
            )}
          </section>
        </div>

        <aside className="side-card">
          <div className="muted small">Rooms from</div>
          <div className="big">{money(pg.startingRent)} <span className="muted small" style={{ fontWeight: 500 }}>/ month</span></div>
          <p style={{ marginTop: 8 }}>
            {pg.freeBeds > 0
              ? <Badge value="free" />
              : <Badge value="full" />}
            {' '}
            {pg.freeBeds > 0 ? `${pg.freeBeds} ${pg.freeBeds === 1 ? 'bed' : 'beds'} free right now` : 'No beds free right now'}
          </p>
          <hr />
          <div className="muted small">Owner contact</div>
          {pg.contactPhone ? (
            <a className="phone" href={`tel:${pg.contactPhone}`}>{pg.contactPhone}</a>
          ) : user ? (
            <p>The owner has not added a number.</p>
          ) : (
            <p>
              <Link to="/login" state={{ from: location.pathname }}>Log in</Link> or <Link to="/register" state={{ from: location.pathname }}>sign up</Link> to see the phone number.
            </p>
          )}
          <hr />
          <button className="btn btn-dark btn-block" onClick={startVisit}>Schedule a visit</button>
          <p className="muted small" style={{ marginTop: 6 }}>See the rooms in person. Free, no commitment.</p>
          <hr />
          <a className="btn btn-ghost btn-block" href={mapUrl} target="_blank" rel="noopener noreferrer">
            <PinIcon width={16} height={16} style={{ verticalAlign: '-3px', marginRight: 6 }} />
            View on map
          </a>
        </aside>
      </div>

      {visiting && (
        <Modal title={`Visit ${pg.name}`} onClose={() => setVisiting(false)}>
          <form onSubmit={sendVisit}>
            <p className="muted small">Pick a day and time. The owner confirms, and you will see the answer in “My Visits”.</p>
            <VisitPicker onPick={setVisit} />
            <Field label="Anything the owner should know? (optional)">
              <textarea rows="2" maxLength={300} value={visitNote} onChange={(e) => setVisitNote(e.target.value)} />
            </Field>
            <button className="btn btn-block" disabled={saving || !visit}>
              {saving ? 'Sending…' : visit ? 'Request this visit' : 'Pick a day and time'}
            </button>
          </form>
        </Modal>
      )}

      {booking && (
        <Modal title={`${onRequest ? 'Request' : 'Book'} room ${booking.roomNumber}`} onClose={() => setBooking(null)}>
          <form onSubmit={confirm}>
            <p>
              {label(booking.type)} room at {pg.name} · <strong>{money(booking.monthlyRent)}</strong> per month.
              {onRequest
                ? 'The owner approves bookings here. Your bed is held for 48 hours while they decide.'
                : 'Your bed is reserved as soon as you confirm.'}
            </p>
            <Field label="Move-in date">
              <input type="date" required min={iso(today)} max={iso(maxDate)} value={form.moveInDate}
                onChange={(e) => setForm({ ...form, moveInDate: e.target.value })} />
            </Field>
            <Field label="Emergency contact name (optional)">
              <input maxLength={100} value={form.emergencyContactName} onChange={(e) => setForm({ ...form, emergencyContactName: e.target.value })} />
            </Field>
            <Field label="Emergency contact phone (optional)">
              <input maxLength={15} value={form.emergencyContactPhone} onChange={(e) => setForm({ ...form, emergencyContactPhone: e.target.value })} />
            </Field>
            <button className="btn btn-block" disabled={saving}>{saving ? 'Sending…' : onRequest ? 'Send request' : 'Confirm booking'}</button>
          </form>
        </Modal>
      )}
    </>
  )
}
