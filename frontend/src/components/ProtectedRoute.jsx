import { Navigate, useLocation } from 'react-router-dom'
import { useAuth, homeFor } from '../context/AuthContext'
import { Loader } from './UI'

// Wrap a page with this to require login (and optionally a specific role).
// NOTE: this only decides what to SHOW. It is not a security boundary - the backend
// re-checks the token and role on every API call.
export default function ProtectedRoute({ role, children }) {
  const { user, checking } = useAuth()
  const location = useLocation()
  if (checking) return <Loader /> // wait until the server has confirmed who the user is
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  if (role && user.role !== role) return <Navigate to={homeFor(user.role)} replace />
  return children
}
