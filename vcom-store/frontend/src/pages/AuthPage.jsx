import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { useStoreCtx } from '../store.jsx'
import { errorMessage } from '../api'

// scope="store": a customer of the current store.  scope="merchant": a merchant account.
export default function AuthPage({ scope, mode }) {
  const isRegister = mode === 'register'
  const ctx = useStoreCtx()
  const inStore = scope === 'store' && ctx
  const base = inStore ? ctx.base : '/merchant'
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', email: '', phone: '', identifier: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const merchantId = inStore ? ctx.store.ownerId : undefined

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      const user = isRegister
        ? await register({ name: form.name, email: form.email, phone: form.phone, password: form.password, merchantId })
        : await login(form.identifier, form.password, merchantId)
      if (inStore) navigate(ctx.base)
      else if (user.role === 'ADMIN') navigate('/admin')
      else navigate(isRegister ? '/merchant/onboarding' : '/dashboard')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const title = inStore
    ? (isRegister ? `Create your ${ctx.store.name} account` : `Sign in to ${ctx.store.name}`)
    : (isRegister ? 'Open your store' : 'Merchant sign in')

  return (
    <form className="panel" onSubmit={submit}>
      <h1>{title}</h1>
      {!inStore && <p className="muted">For merchants. Shopping? Open a store from the home page and sign in there.</p>}
      {isRegister ? (
        <>
          <label>Name<input required value={form.name} onChange={set('name')} autoComplete="name" /></label>
          <label>Email<input required type="email" value={form.email} onChange={set('email')} autoComplete="email" /></label>
          <label>Mobile number
            <input required type="tel" inputMode="tel" value={form.phone} onChange={set('phone')} autoComplete="tel" placeholder="10-digit mobile number" />
            <span className="muted">Used for delivery updates and to reset your password.</span>
          </label>
        </>
      ) : (
        <label>Email or mobile number
          <input required value={form.identifier} onChange={set('identifier')} autoComplete="username" />
        </label>
      )}
      <label>Password
        <input required type="password" minLength={isRegister ? 8 : undefined} value={form.password} onChange={set('password')} autoComplete={isRegister ? 'new-password' : 'current-password'} />
        {isRegister && <span className="muted">At least 8 characters.</span>}
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button disabled={busy}>{busy ? 'Please wait…' : isRegister ? 'Create account' : 'Sign in'}</button>
      {!isRegister && <p className="muted" style={{ margin: 0 }}><Link to={`${base}/forgot-password`}>Forgot password?</Link></p>}
      <p className="muted">
        {isRegister
          ? <>Already have an account? <Link to={`${base}/login`}>Sign in</Link></>
          : <>New here? <Link to={`${base}/register`}>{inStore ? 'Create an account' : 'Open a store'}</Link></>}
      </p>
    </form>
  )
}
