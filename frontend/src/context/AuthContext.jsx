import { createContext, useContext, useEffect, useState } from 'react'
import { authStorage } from '../api/storage'
import { LOGOUT_EVENT } from '../api/axios'
import axios from 'axios'
import { authApi } from '../api/services'
import { useToast } from './ToastContext'

const AuthContext = createContext(null)
const isCancel = (err) => axios.isCancel(err)

/*
 * IMPORTANT - what is and is not "security" in this frontend:
 *   The role stored in the browser is only used to decide which MENUS and PAGES to show.
 *   Anyone can edit localStorage, so it can NEVER protect anything.
 *   The real protection is the backend: every /api/owner, /api/tenant and /api/admin
 *   endpoint checks the token and the role again, against the database.
 *
 * To avoid showing the wrong screens, on start-up we ask the server "who am I?" (/auth/me)
 * and replace whatever was stored with the server's answer.
 */
export function AuthProvider({ children }) {
  const toast = useToast()
  const [user, setUser] = useState(() => (authStorage.getToken() ? authStorage.getUser() : null))
  // true while we are asking the server to confirm a stored session
  const [checking, setChecking] = useState(() => Boolean(authStorage.getToken()))

  // 1) Verify the stored session once, when the app opens
  useEffect(() => {
    if (!authStorage.getToken()) {
      setChecking(false)
      return
    }
    let cancelled = false
    authApi.me()
      .then(({ data }) => {
        if (cancelled) return
        const verified = { name: data.name, email: data.email, role: data.role }
        authStorage.saveUser(verified)
        setUser(verified) // role now comes from the server, not from localStorage
      })
      .catch((err) => {
        if (cancelled) return
        // 401 is already handled by the axios interceptor (it fires the logout event).
        // If the server is simply unreachable we keep the stored user for display only;
        // the backend still refuses anything the user is not allowed to do.
        if (err.response?.status === 401 || isCancel(err)) setUser(null)
      })
      .finally(() => { if (!cancelled) setChecking(false) })
    return () => { cancelled = true }
  }, [])

  // 2) Any 401 / expired token anywhere in the app -> clear React state too
  useEffect(() => {
    const onLogout = (e) => {
      setUser(null)
      if (e.detail) toast(e.detail, 'error')
    }
    window.addEventListener(LOGOUT_EVENT, onLogout)
    return () => window.removeEventListener(LOGOUT_EVENT, onLogout)
  }, [toast])

  const saveLogin = (authResponse) => {
    const u = { name: authResponse.name, email: authResponse.email, role: authResponse.role }
    authStorage.save(authResponse.token, u)
    setUser(u)
    return u
  }

  const logout = () => {
    authStorage.clear()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, checking, saveLogin, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)

// Where each role lands after login
export const homeFor = (role) =>
  role === 'OWNER' ? '/owner/dashboard' : role === 'TENANT' ? '/tenant/profile' : '/admin/pgs'
