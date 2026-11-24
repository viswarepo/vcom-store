import { useEffect, useState } from 'react'
import api, { errorMessage } from '../api'
import { limitLabel, priceLabel } from '../plans.js'

const blank = { name: '', description: '', price: '0', billingPeriod: 'MONTHLY', commissionPercent: '10', maxProducts: '', active: true, sortOrder: 0 }

// Platform owner: create and edit the plans merchants can buy.
export default function AdminPlans() {
  const [plans, setPlans] = useState([])
  const [form, setForm] = useState(blank)
  const [editingId, setEditingId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = () => api.get('/payments/admin/plans').then((r) => setPlans(r.data)).catch((e) => setError(errorMessage(e)))
  useEffect(() => { load() }, [])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const save = async (e) => {
    e.preventDefault()
    setError(''); setNotice('')
    const body = {
      ...form,
      price: Number(form.price),
      commissionPercent: Number(form.commissionPercent),
      maxProducts: form.maxProducts === '' ? null : Number(form.maxProducts),
      sortOrder: Number(form.sortOrder),
    }
    try {
      if (editingId) await api.put(`/payments/admin/plans/${editingId}`, body)
      else await api.post('/payments/admin/plans', body)
      setNotice(editingId ? 'Plan saved' : 'Plan created')
      setForm(blank); setEditingId(null); load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const edit = (p) => {
    setEditingId(p.id)
    setForm({ name: p.name, description: p.description || '', price: p.price, billingPeriod: p.billingPeriod,
      commissionPercent: p.commissionPercent, maxProducts: p.maxProducts ?? '', active: p.active, sortOrder: p.sortOrder })
  }

  return (
    <>
      <h2>Subscription plans</h2>
      <p className="muted">Merchants pick one when they open a store. Changing a plan doesn’t affect merchants who already paid for it: they keep the price, commission and limits they bought until they renew.</p>
      <table className="table">
        <tbody>
          {plans.map((p) => (
            <tr key={p.id}>
              <td><strong>{p.name}</strong>{!p.active && <span className="muted"> (hidden)</span>}<span className="muted block">{p.description}</span></td>
              <td>{priceLabel(p)}</td>
              <td className="muted">{Number(p.commissionPercent)}% · {limitLabel(p.maxProducts)}</td>
              <td className="actions"><button className="ghost" onClick={() => edit(p)}>Edit</button></td>
            </tr>
          ))}
        </tbody>
      </table>

      <form className="panel wide" onSubmit={save}>
        <h3 style={{ margin: 0 }}>{editingId ? 'Edit plan' : 'New plan'}</h3>
        <div className="two">
          <label>Name<input required value={form.name} onChange={set('name')} /></label>
          <label>Price (INR, 0 = free)<input required type="number" min="0" step="0.01" value={form.price} onChange={set('price')} /></label>
        </div>
        <label>Description<input value={form.description} onChange={set('description')} /></label>
        <div className="two">
          <label>Billing period
            <select value={form.billingPeriod} onChange={set('billingPeriod')}><option value="MONTHLY">Monthly</option><option value="YEARLY">Yearly</option></select>
          </label>
          <label>Commission on sales (%)<input required type="number" min="0" max="100" step="0.01" value={form.commissionPercent} onChange={set('commissionPercent')} /></label>
        </div>
        <div className="two">
          <label>Product limit (empty = unlimited)<input type="number" min="1" step="1" value={form.maxProducts} onChange={set('maxProducts')} /></label>
          <label>Display order<input type="number" step="1" value={form.sortOrder} onChange={set('sortOrder')} /></label>
        </div>
        <label className="check"><input type="checkbox" checked={form.active} onChange={set('active')} /> Offer this plan to new merchants</label>
        {error && <p className="error" role="alert">{error}</p>}
        {notice && <p className="ok" role="status">{notice}</p>}
        <div className="row" style={{ justifyContent: 'flex-start' }}>
          <button>{editingId ? 'Save plan' : 'Create plan'}</button>
          {editingId && <button type="button" className="ghost" onClick={() => { setEditingId(null); setForm(blank) }}>Cancel</button>}
        </div>
      </form>
    </>
  )
}
