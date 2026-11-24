import { useEffect, useState } from 'react'
import api, { errorMessage } from '../api'

// The store owner manages their own store admins here.
export default function Staff() {
  const [staff, setStaff] = useState([])
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = () => api.get('/auth/staff').then((r) => setStaff(r.data)).catch((e) => setError(errorMessage(e)))
  useEffect(() => { load() }, [])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const add = async (e) => {
    e.preventDefault()
    setError(''); setNotice('')
    try {
      await api.post('/auth/staff', form)
      setNotice(`${form.name} can now sign in at /merchant/login`)
      setForm({ name: '', email: '', password: '' })
      load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const remove = async (s) => {
    if (!window.confirm(`Remove ${s.name}? They lose access when their current session ends.`)) return
    try { await api.delete(`/auth/staff/${s.id}`); load() } catch (err) { setError(errorMessage(err)) }
  }

  return (
    <>
      <h2>Store admins ({staff.length})</h2>
      <p className="muted">Store admins can manage products, stock, orders and see customers. They can’t change store settings, see earnings or manage staff.</p>
      <form className="panel wide" onSubmit={add}>
        <div className="two">
          <label>Name<input required value={form.name} onChange={set('name')} /></label>
          <label>Email<input required type="email" value={form.email} onChange={set('email')} /></label>
        </div>
        <label>Temporary password<input required type="password" minLength={6} value={form.password} onChange={set('password')} autoComplete="new-password" /></label>
        {error && <p className="error" role="alert">{error}</p>}
        {notice && <p className="ok" role="status">{notice}</p>}
        <div><button>Add store admin</button></div>
      </form>
      <table className="table">
        <tbody>
          {staff.map((s) => (
            <tr key={s.id}>
              <td>{s.name}</td>
              <td className="muted">{s.email}</td>
              <td className="muted">Added {new Date(s.createdAt).toLocaleDateString()}</td>
              <td className="actions"><button className="ghost danger" onClick={() => remove(s)}>Remove</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
