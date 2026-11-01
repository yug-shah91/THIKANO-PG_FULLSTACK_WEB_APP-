import { useEffect, useState } from 'react'
import { useBusy } from '../../hooks'
import { Link } from 'react-router-dom'
import { bookingApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import { PageHeader, Badge, Loader, Empty, money, date } from '../../components/UI'

const hoursLeft = (iso) => Math.max(0, Math.round((new Date(iso) - Date.now()) / 3600000))

export default function Booking() {
  const toast = useToast()
  const [busy, run] = useBusy()
  const [items, setItems] = useState(null)

  const load = () => bookingApi.mine().then((r) => setItems(r.data)).catch((e) => { toast(errMsg(e), 'error'); setItems([]) })
  useEffect(() => {
    load()
    const t = setInterval(load, 30000) // the owner's answer shows up without a refresh
    return () => clearInterval(t)
  }, [])

  const cancel = (id) => run(() => cancelNow(id))
  const cancelNow = async (id) => {
    try { await bookingApi.cancel(id); toast('Request cancelled. The bed was released.'); load() } catch (e) { toast(errMsg(e), 'error') }
  }

  return (
    <>
      <PageHeader title="My Booking" subtitle="Booking requests you sent to PGs that approve each booking">
        <span className="live"><i /> Live</span>
        <Link className="btn btn-sm" to="/pgs">Find a PG</Link>
      </PageHeader>
      {!items ? <Loader /> : items.length === 0 ? <Empty text="No booking requests. PGs that book instantly go straight to My Stay." /> : (
        <div className="list">
          {items.map((b) => (
            <div className="card" key={b.id}>
              <div className="row-between">
                <h3><Link to={`/pgs/${b.pgId}`}>{b.pgName}</Link> · Room {b.roomNumber}</h3>
                <Badge value={b.status === 'APPROVED' ? 'CONFIRMED' : b.status} />
              </div>
              <p className="muted small">{money(b.monthlyRent)} / month · move-in {date(b.moveInDate)}</p>
              {b.status === 'PENDING' && (
                <>
                  <p>Waiting for the owner. Your bed is held for about <strong>{hoursLeft(b.expiresAt)} more hours</strong>.</p>
                  <button disabled={busy} className="btn btn-ghost btn-sm" onClick={() => cancel(b.id)}>Cancel request</button>
                </>
              )}
              {b.status === 'APPROVED' && (
                <p>Approved! See <Link to="/tenant/profile">My Stay</Link>.{b.ownerPhone && <> Owner: <a className="phone" href={`tel:${b.ownerPhone}`}>{b.ownerPhone}</a></>}</p>
              )}
              {b.status === 'DECLINED' && <p>The owner declined this request.{b.ownerNote ? ` “${b.ownerNote}”` : ''}</p>}
              {b.status === 'EXPIRED' && <p className="muted">The owner did not answer in time, so the bed was released. You can send a new request.</p>}
              {b.status === 'CANCELLED' && <p className="muted">You cancelled this request.</p>}
            </div>
          ))}
        </div>
      )}
    </>
  )
}
