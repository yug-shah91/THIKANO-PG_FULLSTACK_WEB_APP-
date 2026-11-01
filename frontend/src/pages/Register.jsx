import { useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '../api/services'
import { errMsg } from '../api/axios'
import { useAuth, homeFor } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { Field } from '../components/UI'
import Brand from '../components/Brand'

// Anyone can sign up as a tenant (to find and book a PG) or as an owner (to manage one).
export default function Register() {
  const [params] = useSearchParams()
  const [role, setRole] = useState(params.get('role') === 'OWNER' ? 'OWNER' : 'TENANT')
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '' })
  const [loading, setLoading] = useState(false)
  const { saveLogin } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const from = useLocation().state?.from

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      const { data } = await authApi.register({ ...form, role })
      const user = saveLogin(data)
      toast(role === 'OWNER' ? 'Account created. Now add your PG.' : 'Account created. Find a PG to book.')
      // owners go to the dashboard, tenants go back to the PG they were looking at (or the PG list)
      navigate(role === 'OWNER' ? homeFor(user.role) : (from || '/pgs'))
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
        <h1>Create your account</h1>
        <p className="muted">What do you want to do?</p>

        <div className="role-switch">
          <button type="button" className={role === 'TENANT' ? 'on' : ''} onClick={() => setRole('TENANT')}>
            <strong>Find a PG</strong>
            <span>Search and book a room</span>
          </button>
          <button type="button" className={role === 'OWNER' ? 'on' : ''} onClick={() => setRole('OWNER')}>
            <strong>List my PG</strong>
            <span>Manage rooms, tenants, rent</span>
          </button>
        </div>

        <Field label="Full name"><input name="name" value={form.name} onChange={change} required /></Field>
        <Field label="Email"><input name="email" type="email" value={form.email} onChange={change} required /></Field>
        <Field label="Phone"><input name="phone" maxLength={15} value={form.phone} onChange={change} /></Field>
        <Field label="Password (8 or more characters)">
          <input name="password" type="password" minLength={8} maxLength={72} value={form.password} onChange={change} required />
        </Field>
        <button className="btn btn-block" disabled={loading}>{loading ? 'Creating…' : 'Create account'}</button>
        <p className="muted center">Already have an account? <Link to="/login" state={{ from }}>Log in</Link></p>
      </form>
    </div>
  )
}
