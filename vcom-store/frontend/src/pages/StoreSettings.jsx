import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'

const blank = { name: '', description: '', logoUrl: '', address: '', gstin: '', themeColor: '#1f6f5c' }

export default function StoreSettings() {
  const [store, setStore] = useState(null)   // saved store, if any
  const [form, setForm] = useState(blank)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    api.get('/stores/mine')
      .then((r) => { setStore(r.data); setForm({ ...blank, ...r.data }) })
      .catch((e) => { if (e.response?.status !== 404) setError(errorMessage(e)) })
      .finally(() => setLoaded(true))
  }, [])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const save = async (e) => {
    e.preventDefault()
    setError(''); setNotice('')
    try {
      const body = { name: form.name, description: form.description, logoUrl: form.logoUrl, address: form.address, gstin: form.gstin, themeColor: form.themeColor }
      const r = store ? await api.put('/stores/mine', body) : await api.post('/stores', body)
      setStore(r.data)
      setNotice(store ? 'Store saved' : 'Store created')
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (!loaded) return <p className="empty">Loading…</p>

  return (
    <form className="panel" onSubmit={save}>
      <Link to="/dashboard" className="muted">← My products</Link>
      <h1>{store ? 'Store settings' : 'Open your store'}</h1>
      <label>Store name<input required value={form.name} onChange={set('name')} /></label>
      <label>Description<textarea rows="3" value={form.description || ''} onChange={set('description')} /></label>
      <label>Logo URL<input type="url" value={form.logoUrl || ''} onChange={set('logoUrl')} /></label>
      <label>Business address (printed on invoices)<textarea rows="3" value={form.address || ''} onChange={set('address')} /></label>
      <label>GSTIN or tax id (optional)<input maxLength={20} value={form.gstin || ''} onChange={set('gstin')} /></label>
      <label>Brand colour<input type="color" value={form.themeColor} onChange={set('themeColor')} /></label>
      {error && <p className="error" role="alert">{error}</p>}
      {notice && <p className="ok" role="status">{notice}</p>}
      <button>{store ? 'Save changes' : 'Create store'}</button>
      {store && <p className="muted">Your store page: <Link to={`/store/${store.slug}`}>/store/{store.slug}</Link></p>}
    </form>
  )
}
