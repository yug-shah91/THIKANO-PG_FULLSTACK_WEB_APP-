import { useEffect, useState } from 'react'
import { rentApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import { PageHeader, Field, Badge, Loader, Empty, money, date, MONTHS } from '../../components/UI'

export default function Rent() {
  const now = new Date()
  const [tab, setTab] = useState('pending') // pending | overdue | report
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [year, setYear] = useState(now.getFullYear())
  const [rows, setRows] = useState(null)      // null = still loading
  const [loadError, setLoadError] = useState('') // message when the list could not be loaded
  const [generating, setGenerating] = useState(false)
  const [paying, setPaying] = useState(null)
  const [saving, setSaving] = useState(false)
  const [notes, setNotes] = useState('')
  const toast = useToast()

  const load = () => {
    setRows(null)
    setLoadError('')
    const req = tab === 'pending' ? rentApi.pending()
      : tab === 'overdue' ? rentApi.overdue()
      : rentApi.report(month, year)
    req.then((r) => setRows(r.data))
      .catch((e) => { setLoadError(errMsg(e)); setRows([]) })
  }

  useEffect(load, [tab, month, year])

  const generate = async () => {
    if (generating) return // ignore double clicks
    setGenerating(true)
    try {
      const { data } = await rentApi.generate()
      toast(typeof data === 'string' ? data : 'Rent generated')
      load() // refresh the table with the new records
    } catch (e) {
      toast('Could not generate rent: ' + errMsg(e), 'error')
    } finally {
      setGenerating(false)
    }
  }

  const markPaid = async (e) => {
    e.preventDefault()
    if (saving) return
    setSaving(true)
    try {
      await rentApi.markPaid(paying.id, notes)
      toast('Marked as paid')
      setPaying(null); setNotes('')
      load()
    } catch (err) {
      toast(errMsg(err), 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader title="Rent" subtitle="Track pending, overdue and monthly rent">
        <button className="btn btn-ghost" onClick={generate} disabled={generating}>
          {generating ? 'Generating…' : "Generate this month's rent"}
        </button>
      </PageHeader>

      <div className="tabs">
        {['pending', 'overdue', 'report'].map((t) => (
          <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>
            {t === 'report' ? 'Monthly report' : t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {tab === 'report' && (
        <div className="row-gap" style={{ marginBottom: 16 }}>
          <select value={month} onChange={(e) => setMonth(Number(e.target.value))}>
            {MONTHS.map((m, i) => <option key={m} value={i + 1}>{m}</option>)}
          </select>
          <input type="number" value={year} onChange={(e) => setYear(Number(e.target.value))} style={{ width: 100 }} />
        </div>
      )}

      {loadError ? (
        <div className="card center">
          <p className="error-text">Could not load rent records: {loadError}</p>
          <button className="btn btn-ghost" onClick={load}>Try again</button>
        </div>
      ) : !rows ? <Loader /> : rows.length === 0 ? <Empty text="No rent records here." /> : (
        <div className="card table-wrap">
          <table>
            <thead>
              <tr><th>Tenant</th><th>Room</th><th>Period</th><th>Base</th><th>Penalty</th><th>Total</th><th>Due</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>
                    <strong>{r.tenantName}</strong>
                    {r.tenantActive === false && <span className="badge b-LEFT" style={{ marginLeft: 6 }}>CHECKED OUT</span>}
                  </td>
                  <td>{r.roomNumber || '—'}</td>
                  <td>{MONTHS[r.month - 1]} {r.year}</td>
                  <td>{money(r.baseAmount)}</td>
                  <td>{money(r.penaltyAmount)}</td>
                  <td><strong>{money(r.totalAmount)}</strong></td>
                  <td>{date(r.dueDate)}</td>
                  <td><Badge value={r.status} /></td>
                  <td>
                    {r.status !== 'PAID' && (
                      <button className="btn btn-sm" onClick={() => setPaying(r)}>Mark paid</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {paying && (
        <Modal title={`Mark paid: ${paying.tenantName}`} onClose={() => setPaying(null)}>
          <form onSubmit={markPaid}>
            <p>Amount received: <strong>{money(paying.totalAmount)}</strong></p>
            <Field label="Notes (optional)">
              <input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Paid by UPI, cash…" />
            </Field>
            <button className="btn" disabled={saving}>{saving ? 'Saving…' : 'Confirm payment'}</button>
          </form>
        </Modal>
      )}
    </>
  )
}
