import { useEffect, useState } from 'react'
import { useBusy } from '../../hooks'
import { visitApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import { PageHeader, Field, Badge, Loader, Empty } from '../../components/UI'
import { dayParts, clock } from '../tenant/Visits'

const TABS = ['UPCOMING', 'PENDING', 'PAST']

export default function Visits() {
  const toast = useToast()
  const [busy, run] = useBusy()
  const [items, setItems] = useState(null)
  const [tab, setTab] = useState('PENDING')
  const [deciding, setDeciding] = useState(null) // { visit, status, ownerNote }

  const load = () => visitApi.all().then((r) => setItems(r.data)).catch((e) => { toast(errMsg(e), 'error'); setItems([]) })
  useEffect(() => {
    load()
    const t = setInterval(load, 30000) // new requests appear on their own
    return () => clearInterval(t)
  }, [])

  const today = new Date().toLocaleDateString('en-CA')
  const isPast = (v) => v.visitDate < today || v.status === 'DECLINED' || v.status === 'CANCELLED'
  const shown = (items || []).filter((v) => {
    if (tab === 'PENDING') return v.status === 'PENDING' && !isPast(v)
    if (tab === 'UPCOMING') return v.status === 'CONFIRMED' && !isPast(v)
    return isPast(v)
  })
  const count = (name) => (items || []).filter((v) => (name === 'PENDING' ? v.status === 'PENDING' && !isPast(v) : name === 'UPCOMING' ? v.status === 'CONFIRMED' && !isPast(v) : isPast(v))).length

  const save = (e) => { e.preventDefault(); return run(() => saveNow(e)) }
  const saveNow = async (e) => {
    e.preventDefault()
    try {
      await visitApi.decide(deciding.visit.id, { status: deciding.status, ownerNote: deciding.ownerNote })
      toast(deciding.status === 'CONFIRMED' ? 'Visit confirmed' : 'Visit declined')
      setDeciding(null)
      load()
      window.dispatchEvent(new Event('visits-changed')) // refresh the red number in the menu
    } catch (err) { toast(errMsg(err), 'error') }
  }

  return (
    <>
      <PageHeader title="Visits" subtitle="People who want to see your PG in person">
        <span className="live"><i /> Live</span>
      </PageHeader>
      <div className="filters" style={{ marginTop: 0 }}>
        {TABS.map((t) => (
          <button key={t} className={`chip ${tab === t ? 'on' : ''}`} onClick={() => setTab(t)}>
            {t[0] + t.slice(1).toLowerCase()} {items ? `(${count(t)})` : ''}
          </button>
        ))}
      </div>

      {!items ? <Loader /> : shown.length === 0 ? <Empty text={tab === 'PENDING' ? 'No visit requests waiting.' : 'Nothing here.'} /> : (
        <div className="list">
          {shown.map((v) => {
            const p = dayParts(v.visitDate)
            return (
              <div className="card visit-card" key={v.id}>
                <div className="visit-date"><small>{p.mon}</small><strong>{p.day}</strong></div>
                <div className="visit-body">
                  <div className="row-between">
                    <h3>{v.visitorName}</h3>
                    <Badge value={v.status} />
                  </div>
                  <p className="muted small">{p.wd}, {clock(v.visitTime)}{v.visitorPhone ? ` · ` : ''}{v.visitorPhone && <a className="phone" href={`tel:${v.visitorPhone}`}>{v.visitorPhone}</a>}</p>
                  {v.note && <p>“{v.note}”</p>}
                  {v.ownerNote && <p className="reply"><strong>Your note:</strong> {v.ownerNote}</p>}
                  {!isPast(v) && (
                    <div className="visit-actions">
                      {v.status === 'PENDING' && <button className="btn btn-sm" onClick={() => setDeciding({ visit: v, status: 'CONFIRMED', ownerNote: '' })}>Confirm</button>}
                      <button className="btn btn-ghost btn-sm" onClick={() => setDeciding({ visit: v, status: 'DECLINED', ownerNote: '' })}>
                        {v.status === 'CONFIRMED' ? 'Cancel visit' : 'Decline'}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {deciding && (
        <Modal title={deciding.status === 'CONFIRMED' ? 'Confirm visit' : 'Decline visit'} onClose={() => setDeciding(null)}>
          <form onSubmit={save}>
            <p>{deciding.visit.visitorName} · {dayParts(deciding.visit.visitDate).wd}, {clock(deciding.visit.visitTime)}</p>
            <Field label={deciding.status === 'CONFIRMED' ? 'Message (optional), e.g. "Ask for Ravi at the gate"' : 'Reason / another time (optional)'}>
              <textarea rows="3" maxLength={300} value={deciding.ownerNote} onChange={(e) => setDeciding({ ...deciding, ownerNote: e.target.value })} />
            </Field>
            <button disabled={busy} className="btn btn-block">{deciding.status === 'CONFIRMED' ? 'Confirm visit' : 'Decline visit'}</button>
          </form>
        </Modal>
      )}
    </>
  )
}
