import { useEffect, useState } from 'react'
import { bookingApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import { PageHeader, Field, Badge, Loader, Empty, money, date } from '../../components/UI'

const hoursLeft = (iso) => Math.max(0, Math.round((new Date(iso) - Date.now()) / 3600000))

export default function Bookings() {
  const toast = useToast()
  const [items, setItems] = useState(null)
  const [tab, setTab] = useState('PENDING')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [deciding, setDeciding] = useState(null)
  const [saving, setSaving] = useState(false)

  const load = () => bookingApi.all(tab === 'PENDING', page)
    .then((r) => { setItems(r.data.content); setTotalPages(r.data.totalPages || 1) })
    .catch((e) => { toast(errMsg(e), 'error'); setItems([]) })
  useEffect(() => {
    setItems(null)
    load()
    const t = setInterval(load, 30000)
    return () => clearInterval(t)
  }, [tab, page])

  const shown = items || []

  const save = async (e) => {
    e.preventDefault()
    if (saving) return
    setSaving(true)
    try {
      const { data } = await bookingApi.decide(deciding.booking.id, { status: deciding.status, ownerNote: deciding.ownerNote })
      if (data.status === 'EXPIRED') toast('This request had already expired, so the bed was released.', 'error')
      else toast(deciding.status === 'APPROVED' ? 'Approved. They are now your tenant.' : 'Declined. The bed was released.')
      setDeciding(null)
      load()
      window.dispatchEvent(new Event('visits-changed')) // refreshes the red numbers in the menu
    } catch (err) { toast(errMsg(err), 'error'); load() } finally { setSaving(false) }
  }

  return (
    <>
      <PageHeader title="Booking requests" subtitle="Used when your PG is set to “On request” (My PG page). Requests sent before you change the mode stay valid until you answer them or they expire.">
        <span className="live"><i /> Live</span>
      </PageHeader>
      <div className="filters" style={{ marginTop: 0 }}>
        <button className={`chip ${tab === 'PENDING' ? 'on' : ''}`} onClick={() => { setPage(0); setTab('PENDING') }}>Waiting</button>
        <button className={`chip ${tab === 'DONE' ? 'on' : ''}`} onClick={() => { setPage(0); setTab('DONE') }}>Answered</button>
      </div>

      {!items ? <Loader /> : shown.length === 0 ? <Empty text={tab === 'PENDING' ? 'No requests waiting.' : 'Nothing here yet.'} /> : (
        <div className="list">
          {shown.map((b) => (
            <div className="card" key={b.id}>
              <div className="row-between">
                <h3>{b.requesterName} · Room {b.roomNumber}</h3>
                <Badge value={b.status === 'APPROVED' ? 'CONFIRMED' : b.status} />
              </div>
              <p className="muted small">
                {money(b.monthlyRent)} / month · move-in {date(b.moveInDate)}
                {b.requesterPhone && <> · <a className="phone" href={`tel:${b.requesterPhone}`}>{b.requesterPhone}</a></>}
              </p>
              {b.status === 'PENDING' && (
                <>
                  <p className="muted small">The bed is held for {hoursLeft(b.expiresAt)} more hours. After that it is released.</p>
                  <div className="visit-actions">
                    <button className="btn btn-sm" onClick={() => setDeciding({ booking: b, status: 'APPROVED', ownerNote: '' })}>Approve</button>
                    <button className="btn btn-ghost btn-sm" onClick={() => setDeciding({ booking: b, status: 'DECLINED', ownerNote: '' })}>Decline</button>
                  </div>
                </>
              )}
              {b.ownerNote && <p className="reply"><strong>Your note:</strong> {b.ownerNote}</p>}
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="pager">
          <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>← Prev</button>
          <span>Page {page + 1} of {totalPages}</span>
          <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Next →</button>
        </div>
      )}

      {deciding && (
        <Modal title={deciding.status === 'APPROVED' ? 'Approve booking' : 'Decline booking'} onClose={() => setDeciding(null)}>
          <form onSubmit={save}>
            <p>{deciding.booking.requesterName} · Room {deciding.booking.roomNumber}</p>
            <Field label={deciding.status === 'APPROVED' ? 'Message (optional)' : 'Reason (optional)'}>
              <textarea rows="3" maxLength={300} value={deciding.ownerNote} onChange={(e) => setDeciding({ ...deciding, ownerNote: e.target.value })} />
            </Field>
            <button className="btn btn-block" disabled={saving}>{saving ? 'Saving…' : deciding.status === 'APPROVED' ? 'Approve' : 'Decline'}</button>
          </form>
        </Modal>
      )}
    </>
  )
}
