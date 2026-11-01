import { useEffect, useState } from 'react'
import { inspectionApi } from '../api/services'
import { errMsg } from '../api/axios'
import { useToast } from '../context/ToastContext'
import AuthedImage from './AuthedImage'
import Modal from './Modal'
import { Badge, Field, Loader, date } from './UI'

const CONDITIONS = { GOOD: 'Good', MINOR_DAMAGE: 'Minor damage', DAMAGED: 'Damaged', MISSING: 'Missing' }
const SUGGESTIONS = ['Bed', 'Mattress', 'Wall', 'Floor', 'Window', 'Door', 'Cupboard', 'Fan', 'Light', 'Bathroom']
const stamp = (d) => (d ? new Date(d).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : null)

const CHANGE_TEXT = {
  UNCHANGED: 'Same as move-in',
  WORSE: 'Worse than move-in',
  BETTER: 'Better than move-in',
  ONLY_AT_CHECKOUT: 'Not listed at move-in',
  NOT_RECORDED_AT_CHECKOUT: 'Not checked at move-out',
}

// Used by the owner (for one tenant) and by the tenant (for themselves).
// The server decides who may do what; this screen only hides buttons that would be refused.
export default function InspectionView({ tenantId, isOwner }) {
  const toast = useToast()
  const [list, setList] = useState(null)
  const [compare, setCompare] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [viewing, setViewing] = useState(null) // photo id shown large

  const load = async () => {
    try {
      const r = await inspectionApi.list(tenantId)
      setList(r.data)
      setError('')
      if (r.data.some((i) => i.type === 'CHECK_IN')) {
        inspectionApi.comparison(tenantId).then((c) => setCompare(c.data)).catch(() => setCompare(null))
      } else setCompare(null)
    } catch (e) { setError(errMsg(e)); setList([]) }
  }
  useEffect(() => { load() }, [tenantId])

  // run one action, show errors, reload. "busy" stops double clicks.
  const act = async (fn, okText) => {
    if (busy) return
    setBusy(true)
    try { await fn(); if (okText) toast(okText); await load() } catch (e) { toast(errMsg(e), 'error') } finally { setBusy(false) }
  }

  if (!list) return <Loader />
  const checkIn = list.find((i) => i.type === 'CHECK_IN')
  const checkOut = list.find((i) => i.type === 'CHECK_OUT')

  return (
    <>
      {error && <p className="error-text">{error}</p>}
      <p className="muted small insp-intro">
        A shared record of the room. Both of you add what you see, with photos, then each presses <b>Confirm</b>.
        When both have confirmed it is locked, so nobody can change it later.
      </p>

      {isOwner && (
        <div className="visit-actions" style={{ marginBottom: 14 }}>
          {!checkIn && <button className="btn" disabled={busy} onClick={() => act(() => inspectionApi.create({ tenantId, type: 'CHECK_IN' }), 'Move-in report started')}>Start move-in report</button>}
          {checkIn && !checkOut && <button className="btn btn-dark" disabled={busy} onClick={() => act(() => inspectionApi.create({ tenantId, type: 'CHECK_OUT' }), 'Move-out report started')}>Start move-out report</button>}
        </div>
      )}

      {!isOwner && list.length === 0 && <p className="empty">Your owner has not started a room report yet.</p>}

      {[checkIn, checkOut].filter(Boolean).map((i) => (
        <InspectionCard key={i.id} insp={i} busy={busy} act={act} onPhoto={setViewing} />
      ))}

      {compare && <Comparison data={compare} onPhoto={setViewing} />}

      {viewing && (
        <Modal title="Photo" onClose={() => setViewing(null)}>
          <AuthedImage photoId={viewing} onClick={() => setViewing(null)} />
        </Modal>
      )}
    </>
  )
}

function InspectionCard({ insp, busy, act, onPhoto }) {
  const [form, setForm] = useState({ area: '', condition: 'GOOD', note: '' })
  const [file, setFile] = useState(null)
  const [itemId, setItemId] = useState('')
  const [caption, setCaption] = useState('')

  const me = insp.viewer
  const mineConfirmed = me === 'OWNER' ? insp.ownerConfirmedAt : insp.tenantConfirmedAt
  const canEdit = !insp.complete && !mineConfirmed
  const title = insp.type === 'CHECK_IN' ? 'Move-in report' : 'Move-out report'

  const addLine = (e) => {
    e.preventDefault()
    act(() => inspectionApi.addItem(insp.id, { area: form.area, condition: form.condition, note: form.note || null }), 'Line added')
      .then(() => setForm({ area: '', condition: 'GOOD', note: '' }))
  }

  const upload = (e) => {
    e.preventDefault()
    if (!file) return
    const fd = new FormData()
    fd.append('file', file)
    if (caption) fd.append('caption', caption)
    if (itemId) fd.append('itemId', itemId)
    act(() => inspectionApi.addPhoto(insp.id, fd), 'Photo added').then(() => { setFile(null); setCaption(''); e.target.reset() })
  }

  return (
    <div className="card insp">
      <div className="row-between">
        <h3>{title} · Room {insp.roomNumber}</h3>
        {insp.complete ? <Badge value="CONFIRMED" /> : <Badge value="PENDING" />}
      </div>
      <p className="muted small">
        Owner: {insp.ownerConfirmedAt ? `confirmed ${stamp(insp.ownerConfirmedAt)}` : 'not confirmed yet'} ·
        Tenant: {insp.tenantConfirmedAt ? ` confirmed ${stamp(insp.tenantConfirmedAt)}` : ' not confirmed yet'}
      </p>
      {insp.complete && <p className="insp-lock">🔒 Locked. Both sides confirmed, so this record can not be changed.</p>}

      <table className="table">
        <thead><tr><th>Part of room</th><th>Condition</th><th>Note</th><th>By</th><th /></tr></thead>
        <tbody>
          {insp.items.length === 0 && <tr><td colSpan="5" className="muted">No lines yet.</td></tr>}
          {insp.items.map((it) => (
            <tr key={it.id}>
              <td><strong>{it.area}</strong></td>
              <td><span className={`cond c-${it.condition}`}>{CONDITIONS[it.condition]}</span></td>
              <td>{it.note || '—'}</td>
              <td className="muted small">{it.party === 'OWNER' ? 'Owner' : 'Tenant'}</td>
              <td>
                {canEdit && it.party === me && !insp.photos.some((p) => p.itemId === it.id) && (
                  <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => act(() => inspectionApi.removeItem(insp.id, it.id))}>Remove</button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {insp.photos.length > 0 && (
        <div className="photos">
          {insp.photos.map((p) => (
            <figure key={p.id} title={`SHA-256 ${p.sha256}`}>
              <AuthedImage photoId={p.id} onClick={() => onPhoto(p.id)} />
              <figcaption>
                {p.caption || (p.itemId ? insp.items.find((i) => i.id === p.itemId)?.area : 'Photo')}
                <br /><span className="muted">{p.party === 'OWNER' ? 'Owner' : 'Tenant'} · {stamp(p.uploadedAt)}</span>
              </figcaption>
            </figure>
          ))}
        </div>
      )}

      {canEdit && (
        <div className="insp-forms">
          <form onSubmit={addLine} className="insp-form">
            <input list="areas" required maxLength={60} placeholder="Part of room (e.g. Window)" value={form.area} onChange={(e) => setForm({ ...form, area: e.target.value })} />
            <datalist id="areas">{SUGGESTIONS.map((s) => <option key={s} value={s} />)}</datalist>
            <select value={form.condition} onChange={(e) => setForm({ ...form, condition: e.target.value })}>
              {Object.entries(CONDITIONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <input maxLength={300} placeholder="Note (optional)" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} />
            <button className="btn btn-sm" disabled={busy}>Add line</button>
          </form>

          <form onSubmit={upload} className="insp-form">
            <input type="file" accept="image/jpeg,image/png,image/webp" required onChange={(e) => setFile(e.target.files[0] || null)} />
            <select value={itemId} onChange={(e) => setItemId(e.target.value)}>
              <option value="">Not tied to a line</option>
              {insp.items.map((it) => <option key={it.id} value={it.id}>{it.area}</option>)}
            </select>
            <input maxLength={120} placeholder="Caption (optional)" value={caption} onChange={(e) => setCaption(e.target.value)} />
            <button className="btn btn-sm btn-dark" disabled={busy || !file}>Upload photo</button>
          </form>
          <p className="muted small">Photos: JPEG, PNG or WebP, up to 5 MB. They can not be deleted afterwards, and the server stamps the time.</p>

          <button className="btn btn-block" disabled={busy || insp.items.length === 0}
            onClick={() => window.confirm('Confirm this report? After this you can not add or remove anything of yours.') && act(() => inspectionApi.confirm(insp.id), 'Confirmed')}>
            I confirm this report is accurate
          </button>
        </div>
      )}
      {mineConfirmed && !insp.complete && <p className="muted small" style={{ marginTop: 10 }}>You confirmed. Waiting for the other side.</p>}
    </div>
  )
}

function Comparison({ data, onPhoto }) {
  return (
    <div className="card insp compare">
      <h3>Move-in vs move-out</h3>
      {data.checkOutMissing ? (
        <p className="muted">No move-out report yet. When the tenant leaves, the owner starts one and the differences show up here.</p>
      ) : (
        <p className={data.possibleNewDamage ? 'error-text' : 'ok-text'}>
          {data.possibleNewDamage
            ? `${data.possibleNewDamage} place${data.possibleNewDamage > 1 ? 's' : ''} look worse than at move-in.`
            : 'Nothing looks worse than at move-in.'}
        </p>
      )}
      {!(data.checkInComplete && (data.checkOutMissing || data.checkOutComplete)) && (
        <p className="muted small">Not everything is confirmed by both sides yet, so treat this as a draft.</p>
      )}
      <div className="cmp-rows">
        {data.rows.map((r) => (
          <div className={`cmp-row ${r.possibleNewDamage ? 'flag' : ''}`} key={r.area}>
            <div className="cmp-area">
              <strong>{r.area}</strong>
              {!data.checkOutMissing && <div className="muted small">{CHANGE_TEXT[r.change]}</div>}
              {r.damageAlreadyAtMoveIn && <div className="muted small">Damage was already there at move-in</div>}
            </div>
            <div className="cmp-cond">
              <span className="muted small">Move-in</span>
              {r.checkIn ? <span className={`cond c-${r.checkIn}`}>{CONDITIONS[r.checkIn]}</span> : <span className="muted">—</span>}
            </div>
            <div className="cmp-cond">
              <span className="muted small">Move-out</span>
              {r.checkOut ? <span className={`cond c-${r.checkOut}`}>{CONDITIONS[r.checkOut]}</span> : <span className="muted">—</span>}
            </div>
            <div className="cmp-photos">
              {[...r.checkInPhotoIds, ...r.checkOutPhotoIds].slice(0, 6).map((id) => (
                <AuthedImage key={id} photoId={id} onClick={() => onPhoto(id)} />
              ))}
            </div>
            {r.notes.length > 0 && <ul className="cmp-notes">{r.notes.map((n, i) => <li key={i}>{n}</li>)}</ul>}
          </div>
        ))}
      </div>
      <p className="muted small">This compares recorded conditions only. Any deposit deduction is still for the owner and tenant to agree.</p>
    </div>
  )
}
