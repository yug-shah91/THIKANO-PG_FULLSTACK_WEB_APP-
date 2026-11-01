import { useEffect, useState } from 'react'
import { useBusy } from '../../hooks'
import { chargeApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import { TagIcon } from '../../components/Icons'
import { PageHeader, Field, Loader, Badge, money } from '../../components/UI'

const KINDS = {
  REQUIRED_MONTHLY: 'Every month (everyone pays)',
  OPTIONAL_MONTHLY: 'Every month (optional)',
  ONE_TIME: 'One time (deposit, joining fee)',
}
// table columns: billing frequency and whether a tenant can opt out
const FREQUENCY = { REQUIRED_MONTHLY: 'Monthly', OPTIONAL_MONTHLY: 'Monthly', ONE_TIME: 'One time' }
const STATUS = { REQUIRED_MONTHLY: 'REQUIRED', OPTIONAL_MONTHLY: 'OPTIONAL', ONE_TIME: 'REQUIRED' }
const EMPTY = { name: '', amount: '', kind: 'REQUIRED_MONTHLY', note: '' }
const IDEAS = ['Electricity', 'Meals', 'Laundry', 'Wi-Fi', 'Housekeeping', 'Security deposit']

export default function Charges() {
  const toast = useToast()
  const [busy, run] = useBusy()
  const [items, setItems] = useState(null)
  const [editing, setEditing] = useState(null) // form values, with id when editing
  const [saving, setSaving] = useState(false)

  const load = () => chargeApi.list().then((r) => setItems(r.data)).catch((e) => { toast(errMsg(e), 'error'); setItems([]) })
  useEffect(() => { load() }, [])

  const save = async (e) => {
    e.preventDefault()
    if (saving) return
    setSaving(true)
    const payload = { name: editing.name, amount: Number(editing.amount), kind: editing.kind, note: editing.note || null }
    try {
      if (editing.id) await chargeApi.update(editing.id, payload)
      else await chargeApi.add(payload)
      toast('Saved')
      setEditing(null)
      load()
    } catch (err) { toast(errMsg(err), 'error') } finally { setSaving(false) }
  }

  const remove = (id) => run(() => removeNow(id))
  const removeNow = async (id) => {
    if (!window.confirm('Remove this charge?')) return
    try { await chargeApi.remove(id); toast('Removed'); load() } catch (err) { toast(errMsg(err), 'error') }
  }

  return (
    <>
      <PageHeader title="Charges" subtitle="Everything tenants pay besides room rent. Honest numbers earn trust.">
        <button className="btn" onClick={() => setEditing({ ...EMPTY })}>Add a charge</button>
      </PageHeader>
      <div className="idea-row">
        <span>Tenants see these on your PG page. Quick add:</span>
        {IDEAS.map((i) => (
          <button key={i} className="chip chip-sm" onClick={() => setEditing({ ...EMPTY, name: i, kind: i === 'Security deposit' ? 'ONE_TIME' : i === 'Meals' || i === 'Laundry' ? 'OPTIONAL_MONTHLY' : 'REQUIRED_MONTHLY' })}>{i}</button>
        ))}
      </div>

      {!items ? <Loader /> : items.length === 0 ? (
        <div className="empty-state">
          <TagIcon width={32} height={32} />
          <h3>No extra charges yet</h3>
          <p>List what tenants pay besides rent, such as electricity, meals or a deposit. Tenants see this before they book.</p>
          <button className="btn" onClick={() => setEditing({ ...EMPTY })}>Add a charge</button>
        </div>
      ) : (
        <div className="data-table">
          <table>
            <thead>
              <tr><th>Charge</th><th className="num">Amount</th><th>Billing frequency</th><th>Status</th><th><span className="sr">Actions</span></th></tr>
            </thead>
            <tbody>
              {items.map((c) => (
                <tr key={c.id}>
                  <td><strong>{c.name}</strong>{c.note && <span className="note">{c.note}</span>}</td>
                  <td className="num">{money(c.amount)}</td>
                  <td>{FREQUENCY[c.kind]}</td>
                  <td><Badge value={STATUS[c.kind]} />{c.kind === 'ONE_TIME' && <span className="note">paid once</span>}</td>
                  <td className="num">
                    <button className="btn btn-ghost btn-sm" onClick={() => setEditing({ ...c, amount: String(c.amount), note: c.note || '' })}>Edit</button>{' '}
                    <button disabled={busy} className="btn btn-ghost btn-sm" onClick={() => remove(c.id)}>Remove</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <Modal title={editing.id ? 'Edit charge' : 'Add a charge'} onClose={() => setEditing(null)}>
          <form onSubmit={save}>
            <Field label="Name"><input required maxLength={60} value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></Field>
            <Field label="Amount (₹)"><input required type="number" min="0" max="100000" value={editing.amount} onChange={(e) => setEditing({ ...editing, amount: e.target.value })} /></Field>
            <Field label="How it is paid">
              <select value={editing.kind} onChange={(e) => setEditing({ ...editing, kind: e.target.value })}>
                {Object.entries(KINDS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </Field>
            <Field label="Note (optional)"><input maxLength={120} placeholder="e.g. 3 meals a day, refunded at checkout" value={editing.note} onChange={(e) => setEditing({ ...editing, note: e.target.value })} /></Field>
            <button className="btn btn-block" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
          </form>
        </Modal>
      )}
    </>
  )
}
