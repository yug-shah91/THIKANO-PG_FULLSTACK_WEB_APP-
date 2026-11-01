import { useEffect, useState } from 'react'
import { useBusy } from '../../hooks'
import { Link } from 'react-router-dom'
import { tenantApi, roomApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import { PageHeader, Field, Badge, Loader, Empty, date } from '../../components/UI'

const EMPTY = {
  name: '', email: '', password: '', phone: '', roomId: '', joinDate: '', expectedLeaveDate: '',
  emergencyContactName: '', emergencyContactPhone: '', idProofType: '', idProofNumber: '',
}

export default function Tenants() {
  const [tenants, setTenants] = useState(null)
  const [search, setSearch] = useState('')
  const [freeRooms, setFreeRooms] = useState([])
  const [adding, setAdding] = useState(null)     // form data or null
  const [allocating, setAllocating] = useState(null) // tenant or null
  const [roomId, setRoomId] = useState('')
  const toast = useToast()
  const [busy, run] = useBusy()

  const load = (name = '') => {
    const req = name.trim() ? tenantApi.search(name.trim()) : tenantApi.all()
    return req.then((r) => setTenants(r.data)).catch((e) => { toast(errMsg(e), 'error'); setTenants([]) })
  }
  const loadRooms = () => roomApi.available().then((r) => setFreeRooms(r.data)).catch(() => {})

  useEffect(() => { load(); loadRooms() }, [])

  const doSearch = (e) => { e.preventDefault(); load(search) }

  const change = (e) => setAdding({ ...adding, [e.target.name]: e.target.value })

  const addTenant = (e) => { e.preventDefault(); return run(() => addTenantNow(e)) }
  const addTenantNow = async (e) => {
    e.preventDefault()
    const payload = { ...adding, roomId: adding.roomId ? Number(adding.roomId) : null }
    // empty date strings must be null, otherwise the backend can't parse them
    if (!payload.joinDate) payload.joinDate = null
    if (!payload.expectedLeaveDate) payload.expectedLeaveDate = null
    try {
      await tenantApi.create(payload)
      toast('Tenant added')
      setAdding(null)
      load(); loadRooms()
    } catch (err) { toast(errMsg(err), 'error') }
  }

  const allocate = (e) => { e.preventDefault(); return run(() => allocateNow(e)) }
  const allocateNow = async (e) => {
    e.preventDefault()
    try {
      await tenantApi.allocate(allocating.id, roomId)
      toast(allocating.roomId ? 'Room changed' : 'Room allocated')
      setAllocating(null); setRoomId('')
      load(); loadRooms()
    } catch (err) { toast(errMsg(err), 'error') }
  }

  const checkout = (t) => run(() => checkoutNow(t))
  const checkoutNow = async (t) => {
    if (!window.confirm(`Check out ${t.name}?\n\nAny unpaid rent stays on record and is still shown on the Rent page.\n\nThe tenant can still log in to see their history and confirm the move-out Room report.`)) return
    try {
      await tenantApi.checkout(t.id)
      toast('Tenant checked out')
      load(); loadRooms()
    } catch (err) { toast(errMsg(err), 'error') }
  }

  if (!tenants) return <Loader />

  return (
    <>
      <PageHeader title="Tenants" subtitle={`${tenants.length} shown`}>
        <form className="search" onSubmit={doSearch}>
          <input placeholder="Search by name…" value={search} onChange={(e) => setSearch(e.target.value)} />
          <button className="btn btn-ghost">Search</button>
        </form>
        <button className="btn" onClick={() => setAdding({ ...EMPTY })}>+ Add tenant</button>
      </PageHeader>

      {tenants.length === 0 ? <Empty text="No tenants found." /> : (
        <div className="card table-wrap">
          <table>
            <thead>
              <tr><th>Name</th><th>Contact</th><th>Room</th><th>Joined</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {tenants.map((t) => (
                <tr key={t.id}>
                  <td><strong>{t.name}</strong></td>
                  <td>{t.email}<br /><span className="muted small">{t.phone}</span></td>
                  <td>{t.roomNumber || '—'}</td>
                  <td>{date(t.joinDate)}</td>
                  <td><Badge value={t.isActive ? 'ACTIVE' : 'LEFT'} /></td>
                  <td className="row-gap">
                    <Link className="btn btn-ghost btn-sm" to={`/owner/tenants/${t.id}/report`}>Room report</Link>
                    {t.isActive && (
                      <button className="btn btn-ghost btn-sm" onClick={() => { setRoomId(''); setAllocating(t) }}>
                        {t.roomId ? 'Move room' : 'Allocate room'}
                      </button>
                    )}
                    {t.isActive && (
                      <button disabled={busy} className="btn btn-danger btn-sm" onClick={() => checkout(t)}>Checkout</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && (
        <Modal title="Add tenant" onClose={() => setAdding(null)}>
          <form onSubmit={addTenant} className="form-grid">
            <Field label="Name"><input name="name" value={adding.name} onChange={change} required /></Field>
            <Field label="Email"><input name="email" type="email" value={adding.email} onChange={change} required /></Field>
            <Field label="Login password (8 or more)"><input name="password" minLength={8} maxLength={72} value={adding.password} onChange={change} required /></Field>
            <Field label="Phone"><input name="phone" value={adding.phone} onChange={change} /></Field>
            <Field label="Room (optional)">
              <select name="roomId" value={adding.roomId} onChange={change}>
                <option value="">Allocate later</option>
                {freeRooms.map((r) => <option key={r.id} value={r.id}>Room {r.roomNumber} ({r.availableBeds} free)</option>)}
              </select>
            </Field>
            <Field label="Join date"><input name="joinDate" type="date" value={adding.joinDate} onChange={change} /></Field>
            <Field label="Expected leave date"><input name="expectedLeaveDate" type="date" value={adding.expectedLeaveDate} onChange={change} /></Field>
            <Field label="Emergency contact name"><input name="emergencyContactName" value={adding.emergencyContactName} onChange={change} /></Field>
            <Field label="Emergency contact phone"><input name="emergencyContactPhone" value={adding.emergencyContactPhone} onChange={change} /></Field>
            <Field label="ID proof type"><input name="idProofType" placeholder="Aadhaar, PAN…" value={adding.idProofType} onChange={change} /></Field>
            <Field label="ID proof number"><input name="idProofNumber" value={adding.idProofNumber} onChange={change} /></Field>
            <div className="full"><button disabled={busy} className="btn">Add tenant</button></div>
          </form>
        </Modal>
      )}

      {allocating && (
        <Modal title={allocating.roomId ? `Move ${allocating.name} to another room` : `Allocate room to ${allocating.name}`} onClose={() => setAllocating(null)}>
          <form onSubmit={allocate}>
            {allocating.roomId && (
              <p className="muted small">Now in Room {allocating.roomNumber}. The old bed is freed only after the new one is taken.</p>
            )}
            <Field label="Choose a room">
              <select value={roomId} onChange={(e) => setRoomId(e.target.value)} required>
                <option value="">Select…</option>
                {freeRooms.filter((r) => r.id !== allocating.roomId).map((r) => <option key={r.id} value={r.id}>Room {r.roomNumber} – {r.type} ({r.availableBeds} free)</option>)}
              </select>
            </Field>
            <button disabled={busy} className="btn">{allocating.roomId ? 'Move' : 'Allocate'}</button>
          </form>
        </Modal>
      )}
    </>
  )
}
