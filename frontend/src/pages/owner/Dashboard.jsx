import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { pgApi, bookingApi, complaintApi, tenantApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { PageHeader, Loader, Badge, money, date, MONTHS } from '../../components/UI'
import { RevenueBars, Donut } from '../../components/Charts'

const ago = (iso) => {
  if (!iso) return ''
  const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} min ago`
  const h = Math.round(mins / 60)
  if (h < 24) return `${h} hour${h === 1 ? '' : 's'} ago`
  const d = Math.round(h / 24)
  return `${d} day${d === 1 ? '' : 's'} ago`
}

export default function Dashboard() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  // the lower panels load on their own, so a slow list never hides the numbers
  const [bookings, setBookings] = useState(null)
  const [complaints, setComplaints] = useState(null)
  const [tenants, setTenants] = useState(null)

  useEffect(() => {
    pgApi.dashboard().then((r) => setData(r.data)).catch((e) => setError(errMsg(e)))
    bookingApi.all(false, 0).then((r) => setBookings(r.data.content || [])).catch(() => setBookings([]))
    complaintApi.byStatus('OPEN').then((r) => setComplaints(r.data || [])).catch(() => setComplaints([]))
    tenantApi.all().then((r) => setTenants(r.data || [])).catch(() => setTenants([]))
  }, [])

  // one list of what happened lately, newest first
  const activity = useMemo(() => {
    const out = []
    ;(tenants || []).forEach((t) => t.registeredAt && out.push({ at: t.registeredAt, text: t.isActive === false ? `${t.name} checked out` : `${t.name} joined${t.roomNumber ? ` room ${t.roomNumber}` : ''}` }))
    ;(complaints || []).forEach((c) => out.push({ at: c.createdAt, text: `${c.tenantName} raised a complaint: ${c.title}` }))
    ;(bookings || []).forEach((b) => out.push({ at: b.createdAt, text: `${b.requesterName} ${b.status === 'PENDING' ? 'requested' : b.status === 'DECLINED' ? 'was declined for' : 'booked'} room ${b.roomNumber}` }))
    return out.filter((a) => a.at).sort((a, b) => new Date(b.at) - new Date(a.at)).slice(0, 6)
  }, [tenants, complaints, bookings])

  if (error) {
    return (
      <>
        <PageHeader title="Dashboard" />
        <div className="empty-state">
          <h3>Set up your PG first</h3>
          <p>{error}</p>
          <Link className="btn" to="/owner/pg">Set up my PG</Link>
        </div>
      </>
    )
  }
  if (!data) return <Loader />

  const trend = data.rentTrend || []
  const now = trend[trend.length - 1]
  const monthName = now ? MONTHS[now.month - 1] : ''
  const overdue = Number(data.totalOutstandingRent) > 0
  const hasRent = trend.some((p) => Number(p.expected) > 0)

  return (
    <>
      <PageHeader title={data.pgName} subtitle="How your PG is doing today" />

      <div className="kpis">
        <div className="kpi"><span>Revenue in {monthName}</span><strong>{money(data.revenueThisMonth)}</strong><small>of {money(data.expectedThisMonth)} due</small></div>
        <div className="kpi"><span>Occupancy</span><strong>{data.occupancyPercent}%</strong><small>{data.occupiedBeds} of {data.totalBeds} beds</small></div>
        <div className="kpi"><span>Available beds</span><strong>{data.availableBeds}</strong><small>in {data.availableRooms} {data.availableRooms === 1 ? 'room' : 'rooms'}</small></div>
        <div className={`kpi ${overdue ? 'warn' : ''}`}><span>Outstanding rent</span><strong>{money(data.totalOutstandingRent)}</strong><small>{data.overdueRentCount} overdue, {data.pendingRentCount} pending</small></div>
      </div>

      <div className="dash-grid">
        <section className="panel navy" aria-labelledby="rev-h">
          <div className="panel-head"><h2 id="rev-h">Rent collected, last 6 months</h2><Link to="/owner/rent">Open rent</Link></div>
          {hasRent ? <RevenueBars points={trend} /> : <p className="muted" style={{ margin: '40px 0' }}>Rent shows here after you generate it for your tenants.</p>}
        </section>

        <section className="panel" aria-labelledby="occ-h">
          <div className="panel-head"><h2 id="occ-h">Occupancy</h2><Link to="/owner/rooms">Rooms</Link></div>
          <div className="donut-wrap">
            <Donut percent={data.occupancyPercent} label={`${data.occupancyPercent}%`} sub="beds taken" />
            <div className="dl">
              <div><span className="muted">Beds taken</span><strong>{data.occupiedBeds}</strong></div>
              <div><span className="muted">Beds free</span><strong>{data.availableBeds}</strong></div>
              <div><span className="muted">Full rooms</span><strong>{data.fullyOccupiedRooms}</strong></div>
              <div><span className="muted">Active tenants</span><strong>{data.activeTenants}</strong></div>
            </div>
          </div>
        </section>

        <section className="panel" aria-labelledby="bk-h">
          <div className="panel-head"><h2 id="bk-h">Recent bookings</h2><Link to="/owner/bookings">All bookings</Link></div>
          {!bookings ? <p className="rows-empty">Loading…</p> : bookings.length === 0 ? <p className="rows-empty">No bookings yet. They appear here when a tenant books a bed.</p> : (
            <ul className="rows">
              {bookings.slice(0, 5).map((b) => (
                <li key={b.id}>
                  <div className="t"><strong>{b.requesterName}, room {b.roomNumber}</strong><span>{money(b.monthlyRent)} a month, move-in {date(b.moveInDate)}</span></div>
                  <Badge value={b.status === 'APPROVED' ? 'CONFIRMED' : b.status} />
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="panel" aria-labelledby="cp-h">
          <div className="panel-head"><h2 id="cp-h">Pending complaints</h2><Link to="/owner/complaints">All complaints</Link></div>
          {!complaints ? <p className="rows-empty">Loading…</p> : complaints.length === 0 ? <p className="rows-empty">Nothing waiting for you.</p> : (
            <ul className="rows">
              {complaints.slice(0, 4).map((c) => (
                <li key={c.id}>
                  <div className="t"><strong>{c.title}</strong><span>{c.tenantName}, room {c.roomNumber || 'n/a'}, {ago(c.createdAt)}</span></div>
                  <Badge value={c.status} />
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="panel" aria-labelledby="ac-h" style={{ gridColumn: '1 / -1' }}>
          <div className="panel-head"><h2 id="ac-h">Recent tenant activity</h2></div>
          {activity.length === 0 ? <p className="rows-empty">Joins, bookings and complaints show up here.</p> : (
            <ul className="timeline">
              {activity.map((a, i) => <li key={i}>{a.text}<span>{ago(a.at)}</span></li>)}
            </ul>
          )}
        </section>
      </div>
    </>
  )
}
