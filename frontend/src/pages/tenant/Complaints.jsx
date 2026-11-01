import { useEffect, useState } from 'react'
import { useBusy } from '../../hooks'
import { complaintApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import { PageHeader, Field, Badge, Loader, Empty, date } from '../../components/UI'

const CATEGORIES = ['ELECTRICAL', 'PLUMBING', 'CLEANLINESS', 'NOISE', 'WIFI', 'SECURITY', 'FOOD', 'OTHER']
const EMPTY = { category: 'ELECTRICAL', title: '', description: '' }

export default function TenantComplaints() {
  const [items, setItems] = useState(null)
  const [form, setForm] = useState(null)
  const toast = useToast()
  const [busy, run] = useBusy()

  const load = () =>
    complaintApi.mine().then((r) => setItems(r.data)).catch((e) => {
      if (e.response?.status !== 404) toast(errMsg(e), 'error') // 404 = nothing booked yet
      setItems([])
    })

  useEffect(() => { load() }, [])

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const submit = (e) => { e.preventDefault(); return run(() => submitNow(e)) }
  const submitNow = async (e) => {
    e.preventDefault()
    try {
      await complaintApi.raise(form)
      toast('Complaint submitted')
      setForm(null)
      load()
    } catch (err) { toast(errMsg(err), 'error') }
  }

  const close = (id) => run(() => closeNow(id))
  const closeNow = async (id) => {
    try {
      await complaintApi.close(id)
      toast('Complaint closed')
      load()
    } catch (err) { toast(errMsg(err), 'error') }
  }

  if (!items) return <Loader />

  return (
    <>
      <PageHeader title="My Complaints" subtitle="Raise issues and track their progress">
        <button className="btn" onClick={() => setForm({ ...EMPTY })}>+ New complaint</button>
      </PageHeader>

      {items.length === 0 ? <Empty text="You haven't raised any complaints." /> : (
        <div className="list">
          {items.map((c) => (
            <div className="card" key={c.id}>
              <div className="row-between"><h3>{c.title}</h3><Badge value={c.status} /></div>
              <p className="muted small">{c.category} · {date(c.createdAt)}</p>
              <p>{c.description}</p>
              {c.ownerResponse && <p className="reply"><strong>Owner:</strong> {c.ownerResponse}</p>}
              {c.status === 'RESOLVED' && (
                <button disabled={busy} className="btn btn-sm" onClick={() => close(c.id)}>Confirm resolved &amp; close</button>
              )}
            </div>
          ))}
        </div>
      )}

      {form && (
        <Modal title="Raise a complaint" onClose={() => setForm(null)}>
          <form onSubmit={submit}>
            <Field label="Category">
              <select name="category" value={form.category} onChange={change}>
                {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
              </select>
            </Field>
            <Field label="Title"><input name="title" value={form.title} onChange={change} required /></Field>
            <Field label="Description"><textarea name="description" rows="4" value={form.description} onChange={change} required /></Field>
            <button disabled={busy} className="btn">Submit</button>
          </form>
        </Modal>
      )}
    </>
  )
}
