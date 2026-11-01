import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { authApi } from '../api/services'
import { errMsg } from '../api/axios'
import { useAuth, homeFor } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { Field } from '../components/UI'
import Brand from '../components/Brand'

export default function Login() {
  const [form, setForm] = useState({ email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const { saveLogin } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const from = location.state?.from // e.g. the PG page the user was trying to book

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      const { data } = await authApi.login(form)
      const user = saveLogin(data)
      navigate(from || homeFor(user.role))
    } catch (err) {
      toast(errMsg(err), 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <Link to="/" className="auth-brand" aria-label="Thikano home"><Brand /></Link>
        <h1>Welcome back</h1>
        <p className="muted">Log in to manage or book a PG</p>
        <Field label="Email">
          <input name="email" type="email" value={form.email} onChange={change} required />
        </Field>
        <Field label="Password">
          <input name="password" type="password" value={form.password} onChange={change} required />
        </Field>
        <button className="btn btn-block" disabled={loading}>{loading ? 'Logging in…' : 'Log in'}</button>
        <p className="muted center">
          New here? <Link to="/register" state={{ from }}>Create an account</Link>
        </p>
        <p className="hint">Looking for a place to stay? <Link to="/pgs">Browse PGs</Link> without an account.</p>
      </form>
    </div>
  )
}
