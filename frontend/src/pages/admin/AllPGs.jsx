import { useEffect, useState } from 'react'
import { pgApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import { PageHeader, Loader, Empty, Badge, money } from '../../components/UI'

export default function AllPGs() {
  const [pgs, setPgs] = useState(null)
  const toast = useToast()

  useEffect(() => {
    pgApi.allForAdmin().then((r) => setPgs(r.data)).catch((e) => { toast(errMsg(e), 'error'); setPgs([]) })
  }, [])

  if (!pgs) return <Loader />

  return (
    <>
      <PageHeader title="All PGs" subtitle={`${pgs.length} registered`} />
      {pgs.length === 0 ? <Empty text="No PGs registered yet." /> : (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>PG</th><th>City</th><th>Owner</th><th>Rooms</th><th>Tenants</th><th>Base rent</th><th>Status</th></tr></thead>
            <tbody>
              {pgs.map((p) => (
                <tr key={p.id}>
                  <td><strong>{p.name}</strong></td>
                  <td>{p.city}</td>
                  <td>{p.ownerName}<br /><span className="muted small">{p.ownerEmail}</span></td>
                  <td>{p.totalRooms}</td>
                  <td>{p.activeTenants}</td>
                  <td>{money(p.baseMonthlyRent)}</td>
                  <td><Badge value={p.isActive ? 'ACTIVE' : 'LEFT'} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
