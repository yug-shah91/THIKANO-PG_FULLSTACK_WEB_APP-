import axios from 'axios'
import { authStorage, isTokenExpired } from './storage'

// One axios instance used by the whole app.
const api = axios.create({ baseURL: '/api' })

// The app (AuthContext) listens for this event and logs the user out of React state too,
// so we never need a hard page reload.
export const LOGOUT_EVENT = 'pg:logout'

function forceLogout(reason) {
  authStorage.clear()
  window.dispatchEvent(new CustomEvent(LOGOUT_EVENT, { detail: reason }))
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
//  401 -> not logged in / token invalid / account deactivated  => log out
//  403 -> logged in but NOT ALLOWED to do this                 => do NOT log out, show the message
//         (the backend uses 403 for permission problems, e.g. an owner touching another PG)
api.interceptors.response.use(
  (res) => res,
  (err) => {
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
  if (!err.response) return 'Cannot reach the server. Is the backend running on port 8080?'
  return 'Something went wrong'
}

export default api
