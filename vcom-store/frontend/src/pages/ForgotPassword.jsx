import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { useStoreCtx } from '../store.jsx'

// Step 1: ask for a code (sent to the mobile number on the account).  Step 2: enter it with a new password.
export default function ForgotPassword({ scope }) {
  const ctx = useStoreCtx()
  const inStore = scope === 'store' && ctx
  const base = inStore ? ctx.base : '/merchant'
  const merchantId = inStore ? ctx.store.ownerId : undefined

  const [step, setStep] = useState(1)
  const [identifier, setIdentifier] = useState('')
  const [otp, setOtp] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [wait, setWait] = useState(0)

  useEffect(() => {
    if (wait <= 0) return
    const t = setTimeout(() => setWait(wait - 1), 1000)
    return () => clearTimeout(t)
  }, [wait])

  const requestCode = async (e) => {
    e?.preventDefault()
    setBusy(true); setError('')
    try {
      const r = await api.post('/auth/forgot-password', { identifier, merchantId })
      setNotice(r.data.message)
      setStep(2)
      setWait(60)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const reset = async (e) => {
    e.preventDefault()
    if (password !== confirm) { setError('The two passwords don’t match'); return }
    setBusy(true); setError('')
    try {
      await api.post('/auth/reset-password', { identifier, merchantId, otp, newPassword: password })
      setStep(3)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (step === 3) {
    return (
      <div className="panel">
        <h1>Password updated</h1>
        <p>You can now sign in with your new password.</p>
        <Link to={`${base}/login`}><button type="button">Sign in</button></Link>
      </div>
    )
  }

  return step === 1 ? (
    <form className="panel" onSubmit={requestCode}>
      <h1>Forgot your password?</h1>
      <p className="muted">Enter the mobile number or email on your account. We’ll send a 6-digit code.</p>
      <label>Mobile number or email
        <input required value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoComplete="username" />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button disabled={busy}>{busy ? 'Please wait…' : 'Send code'}</button>
      <p className="muted"><Link to={`${base}/login`}>Back to sign in</Link></p>
    </form>
  ) : (
    <form className="panel" onSubmit={reset}>
      <h1>Enter your code</h1>
      <p className="ok" role="status" style={{ margin: 0 }}>{notice}</p>
      <label>6-digit code
        <input required inputMode="numeric" pattern="\d{6}" maxLength={6} value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))} autoComplete="one-time-code" />
      </label>
      <label>New password
        <input required type="password" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
        <span className="muted">At least 8 characters.</span>
      </label>
      <label>Confirm new password
        <input required type="password" minLength={8} value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button disabled={busy}>{busy ? 'Please wait…' : 'Set new password'}</button>
      <p className="muted">
        Didn’t get a code?{' '}
        <button type="button" className="link" disabled={wait > 0 || busy} onClick={requestCode}>
          {wait > 0 ? `Send again in ${wait}s` : 'Send again'}
        </button>
      </p>
    </form>
  )
}
