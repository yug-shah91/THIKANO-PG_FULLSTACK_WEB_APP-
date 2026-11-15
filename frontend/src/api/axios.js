import axios from 'axios'
import { authStorage, isTokenExpired } from './storage'
import { WAKING_EVENT } from './wake.js'

// One axios instance used by the whole app.
const api = axios.create({ baseURL: '/api' })

// The app (AuthContext) listens for this event and logs the user out of React state too,
// so we never need a hard page reload.
export const LOGOUT_EVENT = 'pg:logout'

function forceLogout(reason) {
  authStorage.clear()
  window.dispatchEvent(new CustomEvent(LOGOUT_EVENT, { detail: reason }))
}

// ---- Free hosting puts an idle server to sleep; waking it takes about a minute. ----
// While it wakes, the gateway answers 502/503/504 (or the browser gets no answer at all).
// We then wait and try the same request again instead of showing "Something went wrong".
const GATEWAY_CODES = [502, 503, 504]
const MAX_WAKE_RETRIES = 20     // 20 tries x 6 seconds = about 2 minutes
const WAKE_RETRY_MS = 6000
const SAFE_METHODS = ['get', 'head', 'options']

function looksLikeSleepingServer(err) {
  if (axios.isCancel(err) || !err.config) return false
  const safe = SAFE_METHODS.includes((err.config.method || 'get').toLowerCase())
  if (!err.response) return safe && err.code === 'ERR_NETWORK'   // no answer at all (only retried for reads)
  const status = err.response.status
  // 504 can mean "the server got it but was too slow", so only reads are retried on 504.
  if (status === 504) return safe
  return GATEWAY_CODES.includes(status)
}

// Before every request: attach the JWT. If we can already see it has expired, don't even send it.
api.interceptors.request.use((config) => {
  const token = authStorage.getToken()
  if (token) {
    if (isTokenExpired(token)) {
      forceLogout('Your session expired. Please log in again.')
      return Promise.reject(new axios.CanceledError('session expired'))
    }
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// After every response:
//  502/503/504 -> server is probably waking up => wait and retry (banner is shown by WakeBanner)
//  401 -> not logged in / token invalid / account deactivated  => log out
//  403 -> logged in but NOT ALLOWED to do this                 => do NOT log out, show the message
//         (the backend uses 403 for permission problems, e.g. an owner touching another PG)
api.interceptors.response.use(
    (res) => res,
    async (err) => {
      if (looksLikeSleepingServer(err)) {
        const cfg = err.config
        cfg.__wakeRetries = (cfg.__wakeRetries || 0) + 1
        if (cfg.__wakeRetries <= MAX_WAKE_RETRIES) {
          window.dispatchEvent(new Event(WAKING_EVENT))
          await new Promise((resolve) => setTimeout(resolve, WAKE_RETRY_MS))
          return api(cfg)
        }
      }
      const isLoginCall = err.config?.url?.startsWith('/auth/login') || err.config?.url?.startsWith('/auth/register')
      if (err.response?.status === 401 && !isLoginCall) {
        forceLogout('Your session has ended. Please log in again.')
      }
      return Promise.reject(err)
    }
)

// Turns any backend error into a readable message string.
export function errMsg(err) {
  if (axios.isCancel(err)) return 'Your session expired. Please log in again.'
  const data = err.response?.data
  if (data?.fields) return Object.values(data.fields).join(', ')
  if (data?.error) return data.error
  if (err.response?.status === 403) return 'You do not have permission to do this'
  if (err.response?.status === 401) return 'Please log in again'
  if (GATEWAY_CODES.includes(err.response?.status)) {
    return 'The server is still starting up (free hosting). Please wait a minute and try again.'
  }
  if (!err.response) return 'Cannot reach the server. If it was idle it may still be starting up, so please wait a minute and try again.'
  return 'Something went wrong'
}

export default api