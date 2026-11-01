import { useEffect, useState } from 'react'
import { rentApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import { PageHeader, Badge, Loader, Empty, money, date, MONTHS } from '../../components/UI'

export default function TenantRent() {
  const [rows, setRows] = useState(null)
  const toast = useToast()

  useEffect(() => {
    rentApi.myHistory().then((r) => setRows(r.data)).catch((e) => {
      if (e.response?.status !== 404) toast(errMsg(e), 'error') // 404 = nothing booked yet
      setRows([])
    })
  }, [])

  if (!rows) return <Loader />
  const due = rows.filter((r) => r.status !== 'PAID').reduce((s, r) => s + Number(r.totalAmount), 0)

  return (
    <>
      <PageHeader title="My Rent" subtitle={due > 0 ? `You owe ${money(due)}` : 'You are all paid up ✅'} />
      {rows.length === 0 ? <Empty text="No rent yet. Rent shows up here once you have booked a room." /> : (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Month</th><th>Base</th><th>Penalty</th><th>Total</th><th>Due date</th><th>Paid on</th><th>Status</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{MONTHS[r.month - 1]} {r.year}</td>
                  <td>{money(r.baseAmount)}</td>
                  <td>{money(r.penaltyAmount)}</td>
                  <td><strong>{money(r.totalAmount)}</strong></td>
                  <td>{date(r.dueDate)}</td>
                  <td>{date(r.paidAt)}</td>
                  <td><Badge value={r.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
