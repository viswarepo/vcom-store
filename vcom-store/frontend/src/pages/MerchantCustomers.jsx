import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { money } from '../money.js'
import { statusLabel, fulfillmentLabel } from './OrderPage.jsx'

export default function MerchantCustomers() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [open, setOpen] = useState(null)
  const [q, setQ] = useState('')

  useEffect(() => {
    Promise.all([api.get('/auth/customers'), api.get('/orders/merchant/customer-stats'), api.get('/orders/merchant')])
      .then(([c, s, o]) => setData({ customers: c.data, stats: Object.fromEntries(s.data.map((x) => [x.customerId, x])), orders: o.data }))
      .catch((e) => setError(errorMessage(e)))
  }, [])

  if (!data) return error ? <p className="empty error">{error}</p> : <p className="empty">Loading…</p>

  const rows = data.customers
    .map((c) => ({ ...c, orders: data.stats[c.id]?.orders ?? 0, spent: Number(data.stats[c.id]?.spent ?? 0), last: data.stats[c.id]?.lastOrderAt }))
    .filter((c) => !q.trim() || `${c.name} ${c.email} ${c.phone || ''}`.toLowerCase().includes(q.trim().toLowerCase()))
    .sort((a, b) => b.spent - a.spent)

  return (
    <div className="dash">
      <p><Link to="/dashboard" className="muted">← Dashboard</Link></p>
      <h1>Customers ({data.customers.length})</h1>
      <p className="muted">People who created an account on your store. Spend counts paid orders only.</p>
      <div className="toolbar">
        <input type="search" placeholder="Search by name or email" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search customers" />
      </div>
      {rows.length === 0 && <p className="empty">No customers yet. Share your storefront link so people can sign up.</p>}
      <table className="table">
        <thead><tr className="muted"><td>Customer</td><td>Paid orders</td><td>Total spent</td><td>Last order</td><td /></tr></thead>
        <tbody>
          {rows.map((c) => {
            const theirs = data.orders.filter((o) => o.customerId === c.id)
            return (
              <Fragment key={c.id}>
                <tr>
                  <td>{c.name}<span className="muted block">{c.email}{c.phone ? ` · ${c.phone}` : ''}</span></td>
                  <td>{c.orders}</td>
                  <td>{money(c.spent)}</td>
                  <td className="muted">{c.last ? new Date(c.last).toLocaleDateString() : 'Never'}</td>
                  <td className="actions">
                    {theirs.length > 0 && <button className="ghost" onClick={() => setOpen(open === c.id ? null : c.id)}>{open === c.id ? 'Hide' : 'Orders'}</button>}
                  </td>
                </tr>
                {open === c.id && theirs.map((o) => (
                  <tr key={o.id} className="sub">
                    <td colSpan={5}>
                      #{o.id.slice(0, 8)} · {new Date(o.createdAt).toLocaleDateString()} · {o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')} · {money(o.total)} ·{' '}
                      <span className={`status ${o.status}`}>{o.status === 'CONFIRMED' ? fulfillmentLabel[o.fulfillment] : statusLabel[o.status]}</span>
                    </td>
                  </tr>
                ))}
              </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
