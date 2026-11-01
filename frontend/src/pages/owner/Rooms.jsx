import { useEffect, useState } from 'react'
import { useBusy } from '../../hooks'
import { roomApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import Beds from '../../components/Beds'
import { BedIcon, PlusIcon } from '../../components/Icons'
import { PageHeader, Field, Loader, money } from '../../components/UI'

const TYPES = ['SINGLE', 'DOUBLE', 'TRIPLE', 'DORMITORY']
const EMPTY = { roomNumber: '', type: 'SINGLE', capacity: 1, monthlyRent: '', floorNumber: '', amenities: '' }

export default function Rooms() {
  const [rooms, setRooms] = useState(null)
  const [filter, setFilter] = useState('ALL')
  const [editing, setEditing] = useState(null) // null = closed, {} = form data
  const toast = useToast()
  const [busy, run] = useBusy()

  const load = () =>
    roomApi.all().then((r) => setRooms(r.data)).catch((e) => { toast(errMsg(e), 'error'); setRooms([]) })

  useEffect(() => { load() }, [])

  const toggleMaintenance = (room) => run(() => toggleMaintenanceNow(room))
  const toggleMaintenanceNow = async (room) => {
    try {
      await roomApi.maintenance(room.id, room.status !== 'UNDER_MAINTENANCE')
      load()
    } catch (e) { toast(errMsg(e), 'error') }
  }

  const save = (e) => { e.preventDefault(); return run(() => saveNow(e)) }
  const saveNow = async (e) => {
    e.preventDefault()
    const payload = {
      roomNumber: editing.roomNumber,
      type: editing.type,
      capacity: Number(editing.capacity),
      monthlyRent: editing.monthlyRent ? Number(editing.monthlyRent) : null,
      floorNumber: editing.floorNumber !== '' && editing.floorNumber != null ? Number(editing.floorNumber) : null,
      amenities: editing.amenities || null, // empty = clear it
    }
    try {
      if (editing.id) await roomApi.update(editing.id, payload)
      else await roomApi.create(payload)
      toast(editing.id ? 'Room updated' : 'Room added')
      setEditing(null)
      load()
    } catch (err) { toast(errMsg(err), 'error') }
  }

  const change = (e) => setEditing({ ...editing, [e.target.name]: e.target.value })

  if (!rooms) return <Loader />
  const shown = filter === 'ALL' ? rooms : rooms.filter((r) => r.type === filter)
  const usable = rooms.filter((r) => r.status !== 'UNDER_MAINTENANCE')
  const totals = {
    free: usable.reduce((n, r) => n + r.availableBeds, 0),
    taken: rooms.reduce((n, r) => n + r.currentOccupancy, 0),
    maint: rooms.length - usable.length,
  }

  return (
    <>
      <PageHeader title="Rooms" subtitle={`${rooms.length} ${rooms.length === 1 ? "room" : "rooms"} in your PG`}>
        <select value={filter} onChange={(e) => setFilter(e.target.value)}>
          <option value="ALL">All types</option>
          {TYPES.map((t) => <option key={t}>{t}</option>)}
        </select>
        <button className="btn" onClick={() => setEditing({ ...EMPTY })}><PlusIcon width={15} height={15} style={{ verticalAlign: -2 }} /> Add room</button>
      </PageHeader>

      <div className="room-summary">
        <div><strong>{totals.free}</strong><span>beds free</span></div>
        <div><strong>{totals.taken}</strong><span>beds taken</span></div>
        <div><strong>{totals.maint}</strong><span>{totals.maint === 1 ? 'room' : 'rooms'} in maintenance</span></div>
      </div>

      {rooms.length === 0 ? (
        <div className="empty-state">
          <BedIcon width={32} height={32} />
          <h3>No rooms yet</h3>
          <p>Add your rooms with their bed count and rent. Tenants can only book rooms that are listed here.</p>
          <button className="btn" onClick={() => setEditing({ ...EMPTY })}>Add your first room</button>
        </div>
      ) : shown.length === 0 ? (
        <div className="empty-state"><h3>No {filter.toLowerCase()} rooms</h3><p>Pick another room type, or add a room of this type.</p><button className="btn btn-ghost" onClick={() => setFilter('ALL')}>Show all rooms</button></div>
      ) : (
        <div className="room-grid">
          {shown.map((r) => {
            const maint = r.status === 'UNDER_MAINTENANCE'
            const full = !maint && r.availableBeds === 0
            return (
              <div className={`room ${maint ? 'is-maint' : full ? 'is-full' : ''}`} key={r.id}>
                <div className="room-top">
                  <h3>Room {r.roomNumber}</h3>
                  <span className={`avail ${maint ? 'maint' : full ? 'full' : 'ok'}`}>
                    {maint ? 'Under maintenance' : full ? 'Full' : `${r.availableBeds} ${r.availableBeds === 1 ? 'bed' : 'beds'} free`}
                  </span>
                </div>
                <p className="room-meta">{r.type[0] + r.type.slice(1).toLowerCase()}, floor {r.floorNumber ?? 'not set'}</p>
                <div className="room-rent">{money(r.monthlyRent)} <small>per month</small></div>
                <div className="bed-line">
                  <Beds total={r.capacity} taken={r.currentOccupancy} />
                  <span className="muted">{r.currentOccupancy} of {r.capacity} taken</span>
                </div>
                {r.amenities && <p className="muted small" style={{ margin: 0 }}>{r.amenities}</p>}
                <div className="room-actions">
                  <button className="btn btn-ghost btn-sm" onClick={() => setEditing({ ...r })}>Edit room</button>
                  <button disabled={busy} className="btn btn-ghost btn-sm" onClick={() => toggleMaintenance(r)}>
                    {maint ? 'End maintenance' : 'Maintenance'}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {editing && (
        <Modal title={editing.id ? 'Edit room' : 'Add room'} onClose={() => setEditing(null)}>
          <form onSubmit={save} className="form-grid">
            <Field label="Room number"><input name="roomNumber" value={editing.roomNumber} onChange={change} required /></Field>
            <Field label="Type">
              <select name="type" value={editing.type} onChange={change}>
                {TYPES.map((t) => <option key={t}>{t}</option>)}
              </select>
            </Field>
            <Field label={editing.id ? `Capacity (beds) - at least ${editing.currentOccupancy} (current tenants)` : 'Capacity (beds)'}>
              <input name="capacity" type="number" min={Math.max(1, editing.currentOccupancy || 0)} value={editing.capacity} onChange={change} required />
            </Field>
            <Field label="Monthly rent (blank = PG base rent; applies to future rent only)">
              <input name="monthlyRent" type="number" min="1" value={editing.monthlyRent ?? ''} onChange={change} />
            </Field>
            <Field label="Floor"><input name="floorNumber" type="number" value={editing.floorNumber ?? ''} onChange={change} /></Field>
            <Field label="Amenities"><input name="amenities" placeholder="AC, attached bath…" value={editing.amenities ?? ''} onChange={change} /></Field>
            <div className="full"><button disabled={busy} className="btn">Save</button></div>
          </form>
        </Modal>
      )}
    </>
  )
}
