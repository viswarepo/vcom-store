import { useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { useAuth } from '../auth.jsx'
import { useStoreCtx } from '../store.jsx'

// For a signed-in customer (inside a store) or merchant.
export default function ChangePassword() {
  const { user } = useAuth()
  const ctx = useStoreCtx()
  const back = ctx ? ctx.base : (user?.role === 'ADMIN' ? '/admin' : '/dashboard')
  const [form, setForm] = useState({ current: '', next: '', confirm: '' })
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  if (!user) return <p className="empty">Please sign in to change your password.</p>

  const submit = async (e) => {
    e.preventDefault()
    setError(''); setNotice('')
    if (form.next !== form.confirm) { setError('The two new passwords don’t match'); return }
    setBusy(true)
    try {
      const r = await api.post('/auth/change-password', { currentPassword: form.current, newPassword: form.next })
      setNotice(r.data.message)
      setForm({ current: '', next: '', confirm: '' })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="panel" onSubmit={submit}>
      <Link to={back} className="muted">← Back</Link>
      <h1>Change password</h1>
      <label>Current password<input required type="password" value={form.current} onChange={set('current')} autoComplete="current-password" /></label>
      <label>New password<input required type="password" minLength={8} value={form.next} onChange={set('next')} autoComplete="new-password" /><span className="muted">At least 8 characters.</span></label>
      <label>Confirm new password<input required type="password" minLength={8} value={form.confirm} onChange={set('confirm')} autoComplete="new-password" /></label>
      {error && <p className="error" role="alert">{error}</p>}
      {notice && <p className="ok" role="status">{notice}</p>}
      <button disabled={busy}>{busy ? 'Please wait…' : 'Change password'}</button>
    </form>
  )
}
