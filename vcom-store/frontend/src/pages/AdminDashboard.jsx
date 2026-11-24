import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { money } from '../money.js'
import AdminPlans from './AdminPlans.jsx'

// Platform owner: pricing, plans and what merchants generate.
export default function AdminDashboard() {
  const [commission, setCommission] = useState('')
  const [summary, setSummary] = useState(null)
  const [names, setNames] = useState({})
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = () => {
    api.get('/payments/admin/settings').then((r) => setCommission(r.data.commissionPercent)).catch((e) => setError(errorMessage(e)))
    api.get('/payments/admin/summary').then((r) => setSummary(r.data)).catch((e) => setError(errorMessage(e)))
    api.get('/stores').then((r) => setNames(Object.fromEntries(r.data.map((s) => [s.ownerId, s.name])))).catch(() => {})
  }
  useEffect(() => { load() }, [])

  const save = async (e) => {
    e.preventDefault()
    setError(''); setNotice('')
    try {
      await api.put('/payments/admin/settings', { commissionPercent: Number(commission) })
      setNotice('Saved. It applies to merchants who have no active plan.')
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const ownerEarnings = summary ? Number(summary.total.commission) + Number(summary.subscriptionRevenue) : 0

  return (
    <div className="dash">
      <h1>Platform owner</h1>
      <nav className="quick"><Link to="/admin/reports">Sales reports</Link></nav>

      {summary && (
        <>
          <h2 style={{ marginTop: 0 }}>Your earnings</h2>
          <div className="stats">
            <div className="stat"><span className="muted">Total earned</span><strong>{money(ownerEarnings)}</strong></div>
            <div className="stat"><span className="muted">From commission</span><strong>{money(summary.total.commission)}</strong></div>
            <div className="stat"><span className="muted">From subscriptions</span><strong>{money(summary.subscriptionRevenue)}</strong></div>
            <div className="stat"><span className="muted">Paid orders</span><strong>{summary.total.orders}</strong></div>
          </div>
          {Number(summary.total.codCommissionDue) > 0 && (
            <p className="notice">{money(summary.total.codCommissionDue)} of commission is still owed by merchants on cash-on-delivery orders.</p>
          )}
        </>
      )}

      <AdminPlans />

      <form className="panel wide" onSubmit={save} style={{ marginTop: '2rem' }}>
        <h2 style={{ margin: 0 }}>Default commission</h2>
        <label>Commission on each sale (%) for merchants without an active plan
          <input required type="number" min="0" max="100" step="0.01" value={commission} onChange={(e) => setCommission(e.target.value)} />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        {notice && <p className="ok" role="status">{notice}</p>}
        <div><button>Save</button></div>
      </form>

      {summary && (
        <>
          <h2>Merchants ({summary.merchants.length})</h2>
          {summary.merchants.length === 0 && <p className="empty">No paid orders yet.</p>}
          <table className="table">
            <thead>
              <tr className="muted"><td>Merchant</td><td>Orders</td><td>Gross</td><td>Commission</td><td>Merchant share</td></tr>
            </thead>
            <tbody>
              {summary.merchants.map((m) => (
                <tr key={m.merchantId}>
                  <td>{names[m.merchantId] || `Merchant ${m.merchantId.slice(0, 8)}`}</td>
                  <td>{m.summary.orders}</td>
                  <td>{money(m.summary.gross)}</td>
                  <td>{money(m.summary.commission)}</td>
                  <td>{money(m.summary.net)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}
