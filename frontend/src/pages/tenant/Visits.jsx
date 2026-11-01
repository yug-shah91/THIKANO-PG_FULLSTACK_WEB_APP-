import { useEffect, useState } from 'react'
import { useBusy } from '../../hooks'
import { Link } from 'react-router-dom'
import { visitApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import { PageHeader, Badge, Loader, Empty } from '../../components/UI'

export const dayParts = (iso) => {
  const d = new Date(iso + 'T00:00:00')
  return { mon: d.toLocaleDateString('en-IN', { month: 'short' }), day: d.getDate(), wd: d.toLocaleDateString('en-IN', { weekday: 'long' }) }
}
export const clock = (t) => {
  const [h, m] = t.split(':').map(Number)
  return `${h % 12 || 12}:${String(m).padStart(2, '0')} ${h < 12 ? 'AM' : 'PM'}`
}

export default function Visits() {
  const toast = useToast()
  const [busy, run] = useBusy()
  const [items, setItems] = useState(null)

  const load = () => visitApi.mine().then((r) => setItems(r.data)).catch((e) => { toast(errMsg(e), 'error'); setItems([]) })
  useEffect(() => {
    load()
    const t = setInterval(load, 30000) // pick up the owner's answer without a refresh
    return () => clearInterval(t)
  }, [])

  const cancel = (id) => run(() => cancelNow(id))
  const cancelNow = async (id) => {
    try { await visitApi.cancel(id); toast('Visit cancelled'); load() } catch (e) { toast(errMsg(e), 'error') }
  }

  return (
    <>
      <PageHeader title="My Visits" subtitle="PGs you asked to see in person">
        <span className="live"><i /> Live</span>
        <Link className="btn btn-sm" to="/pgs">Find a PG</Link>
      </PageHeader>
      {!items ? <Loader /> : items.length === 0 ? <Empty text="No visits yet. Open a PG and tap “Schedule a visit”." /> : (
        <div className="list">
          {items.map((v) => {
            const p = dayParts(v.visitDate)
            const active = v.status === 'PENDING' || v.status === 'CONFIRMED'
            return (
              <div className="card visit-card" key={v.id}>
                <div className="visit-date"><small>{p.mon}</small><strong>{p.day}</strong></div>
                <div className="visit-body">
                  <div className="row-between">
                    <h3><Link to={`/pgs/${v.pgId}`}>{v.pgName}</Link></h3>
                    <Badge value={v.status} />
                  </div>
                  <p className="muted small">{p.wd}, {clock(v.visitTime)} · {v.pgAddress}</p>
                  {v.ownerNote && <p className="reply"><strong>Owner:</strong> {v.ownerNote}</p>}
                  {v.status === 'CONFIRMED' && v.ownerPhone && <p>Confirmed. Call the owner on <a className="phone" href={`tel:${v.ownerPhone}`}>{v.ownerPhone}</a> if you are running late.</p>}
                  {v.status === 'PENDING' && <p className="muted small">Waiting for the owner to confirm.</p>}
                  {active && (
                    <div className="visit-actions">
                      <button disabled={busy} className="btn btn-ghost btn-sm" onClick={() => cancel(v.id)}>Cancel visit</button>
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </>
  )
}
