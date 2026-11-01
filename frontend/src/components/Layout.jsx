import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { visitApi, bookingApi } from '../api/services'
import { useAuth } from '../context/AuthContext'
import Brand from './Brand'
import {
  DashboardIcon, BuildingIcon, BedIcon, ImageIcon, TagIcon, UsersIcon, WalletIcon, CalendarIcon,
  EyeIcon, StarIcon, MessageIcon, ClipboardIcon, SearchIcon, HomeIcon, LogoutIcon, MenuIcon, ChevronDownIcon,
} from './Icons'

// [path, label, icon] in groups. A group with a null title has no heading.
const MENUS = {
  OWNER: [
    [null, [['/owner/dashboard', 'Dashboard', DashboardIcon]]],
    ['Property', [
      ['/owner/pg', 'My PG', BuildingIcon],
      ['/owner/rooms', 'Rooms', BedIcon],
      ['/owner/charges', 'Charges', TagIcon],
      ['/owner/photos', 'Photos', ImageIcon],
    ]],
    ['People and money', [
      ['/owner/tenants', 'Tenants', UsersIcon],
      ['/owner/rent', 'Rent', WalletIcon],
      ['/owner/bookings', 'Bookings', CalendarIcon],
      ['/owner/visits', 'Visits', EyeIcon],
    ]],
    ['Feedback', [
      ['/owner/reviews', 'Reviews', StarIcon],
      ['/owner/complaints', 'Complaints', MessageIcon],
    ]],
  ],
  TENANT: [
    [null, [['/pgs', 'Find a PG', SearchIcon], ['/tenant/profile', 'My stay', HomeIcon]]],
    ['My account', [
      ['/tenant/booking', 'My booking', CalendarIcon],
      ['/tenant/visits', 'My visits', EyeIcon],
      ['/tenant/report', 'Room report', ClipboardIcon],
      ['/tenant/rent', 'My rent', WalletIcon],
      ['/tenant/complaints', 'My complaints', MessageIcon],
    ]],
  ],
  SUPER_ADMIN: [[null, [['/admin/pgs', 'All PGs', BuildingIcon]]]],
}

const firstName = (n) => (n || '').trim().split(/\s+/)[0] || 'there'

// Compact navy sidebar + top bar (welcome message, profile menu). Pages render in <Outlet />.
export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)   // mobile sidebar
  const [profileOpen, setProfileOpen] = useState(false)
  const profileRef = useRef(null)

  // owners see a live number next to "Visits" and "Bookings" (checked every 30 seconds)
  const [pending, setPending] = useState(0)
  const [pendingBookings, setPendingBookings] = useState(0)
  useEffect(() => {
    if (user.role !== 'OWNER') return
    const check = () => {
      visitApi.pendingCount().then((r) => setPending(r.data.count)).catch(() => {})
      bookingApi.pendingCount().then((r) => setPendingBookings(r.data.count)).catch(() => {})
    }
    check()
    const t = setInterval(check, 30000)
    window.addEventListener('visits-changed', check)
    return () => { clearInterval(t); window.removeEventListener('visits-changed', check) }
  }, [user.role])

  // close menus when the page changes, and the profile menu on outside click / Escape
  useEffect(() => { setMenuOpen(false); setProfileOpen(false) }, [location.pathname])
  useEffect(() => {
    if (!profileOpen) return
    const away = (e) => { if (profileRef.current && !profileRef.current.contains(e.target)) setProfileOpen(false) }
    const esc = (e) => { if (e.key === 'Escape') setProfileOpen(false) }
    document.addEventListener('mousedown', away)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', away); document.removeEventListener('keydown', esc) }
  }, [profileOpen])

  const handleLogout = () => { logout(); navigate('/login') }
  const role = user.role.replace('_', ' ').toLowerCase()

  return (
    <div className="app">
      {menuOpen && <div className="scrim" onClick={() => setMenuOpen(false)} />}
      <aside className={`sidebar ${menuOpen ? 'open' : ''}`}>
        <Link to="/" aria-label="Thikano home"><Brand dark /></Link>
        <nav aria-label="Main">
          {(MENUS[user.role] || []).map(([title, items]) => (
            <div key={title || 'top'} style={{ display: 'contents' }}>
              {title && <div className="nav-group">{title}</div>}
              {items.map(([to, label, Icon]) => (
                <NavLink key={to} to={to} className={({ isActive }) => (isActive ? 'active' : '')}>
                  <Icon width={18} height={18} />
                  {label}
                  {to === '/owner/visits' && pending > 0 && <span className="nav-count">{pending}</span>}
                  {to === '/owner/bookings' && pendingBookings > 0 && <span className="nav-count">{pendingBookings}</span>}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
      </aside>

      <div className="main-col">
        <header className="topbar">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <button className="menu-btn" aria-label="Open menu" onClick={() => setMenuOpen(true)}><MenuIcon /></button>
            <div className="hello">Welcome back, {firstName(user.name)}</div>
          </div>
          <div className="profile" ref={profileRef}>
            <button aria-haspopup="menu" aria-expanded={profileOpen} onClick={() => setProfileOpen((o) => !o)}>
              <span className="avatar-dot">{firstName(user.name)[0].toUpperCase()}</span>
              <span className="small">{firstName(user.name)}</span>
              <ChevronDownIcon width={16} height={16} />
            </button>
            {profileOpen && (
              <div className="profile-menu" role="menu">
                <div className="who"><strong>{user.name}</strong><br /><span className="muted small">{user.email} ({role})</span></div>
                <Link to="/" role="menuitem"><HomeIcon width={16} height={16} /> Thikano home</Link>
                <button role="menuitem" onClick={handleLogout}><LogoutIcon width={16} height={16} /> Log out</button>
              </div>
            )}
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
