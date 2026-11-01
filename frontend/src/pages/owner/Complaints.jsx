import { useEffect, useState } from 'react'
import { useBusy } from '../../hooks'
import { complaintApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import { PageHeader, Field, Badge, Loader, Empty, date } from '../../components/UI'

const STATUSES = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']

// What the owner may change a complaint TO (must match the backend rules).
// CLOSED is only done by the tenant (or the 7-day auto-close).
const NEXT_STATUS = {
  OPEN: ['IN_PROGRESS', 'RESOLVED'],
  IN_PROGRESS: ['IN_PROGRESS', 'RESOLVED'],   // same status = only update the note
  RESOLVED: ['RESOLVED', 'IN_PROGRESS'],
}

export default function Complaints() {
  const [items, setItems] = useState(null)
  const [filter, setFilter] = useState('ALL')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [editing, setEditing] = useState(null)
  const toast = useToast()
  const [busy, run] = useBusy()

  const load = () => {
    setItems(null)
    if (filter === 'ALL') {
      // paginated endpoint: Spring returns { content, totalPages, ... }
      complaintApi.all(page).then((r) => { setItems(r.data.content); setTotalPages(r.data.totalPages || 1) })
        .catch((e) => { toast(errMsg(e), 'error'); setItems([]) })
    } else {
      complaintApi.byStatus(filter).then((r) => { setItems(r.data); setTotalPages(1) })
        .catch((e) => { toast(errMsg(e), 'error'); setItems([]) })
    }
  }

  useEffect(load, [filter, page])

  const save = (e) => { e.preventDefault(); return run(() => saveNow(e)) }
  const saveNow = async (e) => {
    e.preventDefault()
    try {
      await complaintApi.updateStatus(editing.id, { status: editing.status, ownerResponse: editing.ownerResponse })
      toast('Complaint updated')
      setEditing(null)
      load()
    } catch (err) { toast(errMsg(err), 'error') }
  }

  return (
    <>
      <PageHeader title="Complaints" subtitle="Issues raised by your tenants">
        <select value={filter} onChange={(e) => { setPage(0); setFilter(e.target.value) }}>
          <option value="ALL">All</option>
          {STATUSES.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
        </select>
      </PageHeader>

      {!items ? <Loader /> : items.length === 0 ? <Empty text="No complaints. 🎉" /> : (
        <div className="list">
          {items.map((c) => (
            <div className="card" key={c.id}>
              <div className="row-between">
                <h3>{c.title}</h3>
                <Badge value={c.status} />
              </div>
              <p className="muted small">
                {c.category} · {c.tenantName} · Room {c.roomNumber || '—'} · {date(c.createdAt)}
              </p>
              <p>{c.description}</p>
              {c.ownerResponse && <p className="reply"><strong>Your reply:</strong> {c.ownerResponse}</p>}
              {c.status !== 'CLOSED' && (
                <button className="btn btn-ghost btn-sm" onClick={() => setEditing({ ...c, originalStatus: c.status, status: NEXT_STATUS[c.status][0], ownerResponse: c.ownerResponse || '' })}>
                  Update status
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {filter === 'ALL' && totalPages > 1 && (
        <div className="pager">
          <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>← Prev</button>
          <span>Page {page + 1} of {totalPages}</span>
          <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Next →</button>
        </div>
      )}

      {editing && (
        <Modal title={editing.title} onClose={() => setEditing(null)}>
          <form onSubmit={save}>
            <Field label="Status">
              <select value={editing.status} onChange={(e) => setEditing({ ...editing, status: e.target.value })}>
                {(NEXT_STATUS[editing.originalStatus] || []).map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
              </select>
            </Field>
            <Field label="Response to tenant">
              <textarea rows="3" maxLength={400} value={editing.ownerResponse} onChange={(e) => setEditing({ ...editing, ownerResponse: e.target.value })} />
            </Field>
            <button className="btn" disabled={busy}>Save</button>
          </form>
        </Modal>
      )}
    </>
  )
}
