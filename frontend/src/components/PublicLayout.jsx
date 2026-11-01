import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth, homeFor } from '../context/AuthContext'
import Brand from './Brand'

// Header, page area and footer for pages anyone can open (no login needed).
export default function PublicLayout() {
  const { user } = useAuth()
  return (
    <>
      <header className="public-head">
        <div className="public-head-inner">
          <Link to="/" aria-label="Thikano home"><Brand /></Link>
          <nav className="main" aria-label="Main">
            <NavLink to="/pgs">Find a PG</NavLink>
            {!user && <Link to="/register?role=OWNER">List your property</Link>}
          </nav>
          <div className="acct">
            {user ? (
              <Link className="btn btn-ghost btn-sm" to={homeFor(user.role)}>My account</Link>
            ) : (
              <>
                <Link className="btn btn-ghost btn-sm" to="/login">Log in</Link>
                <Link className="btn btn-sm" to="/register">Sign up</Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main className="public-main">
        <Outlet />
      </main>
      <footer className="foot">
        <div className="foot-inner">
          <div>
            <Brand dark />
            <p>Find a paying guest stay with the rent, charges and free beds shown upfront.</p>
          </div>
          <ul aria-label="Footer">
            <li><Link to="/pgs">Find a PG</Link></li>
            <li><Link to="/register?role=OWNER">List your property</Link></li>
            <li><Link to="/login">Log in</Link></li>
          </ul>
        </div>
      </footer>
    </>
  )
}
