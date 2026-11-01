// All auth data lives under OUR OWN keys. We never call localStorage.clear(),
// because that would also wipe anything else stored for this site (theme, filters, ...).
// Every access is wrapped in try/catch: storage can throw (private mode, blocked cookies).
const TOKEN_KEY = 'pg.token'
const USER_KEY = 'pg.user'

export const authStorage = {
  getToken() {
    try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
  },
  getUser() {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch { return null }
  },
  save(token, user) {
    try {
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } catch { /* storage unavailable: user simply has to log in again after a refresh */ }
  },
  saveUser(user) {
    try { localStorage.setItem(USER_KEY, JSON.stringify(user)) } catch { /* ignore */ }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    } catch { /* ignore */ }
  },
}

// Reads the "exp" (expiry, in seconds) from a JWT. This is only used to avoid sending a token
// we already know is dead - the SERVER is what really decides whether a token is valid.
export function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now()
  } catch {
    return true // unreadable token = treat as invalid
  }
}
