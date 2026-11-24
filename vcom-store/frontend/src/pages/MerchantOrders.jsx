import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { money } from '../money.js'
import { statusLabel, fulfillmentLabel } from './OrderPage.jsx'
import Tracking from './Tracking.jsx'

const FILTERS = [['ALL', 'All'], ['TO_SHIP', 'To ship'], ['SHIPPED', 'Shipped'], ['DELIVERED', 'Delivered'], ['CANCELLED', 'Cancelled']]
const COURIERS = ['Delhivery', 'Blue Dart', 'DTDC', 'India Post', 'Ecom Express', 'Xpressbees', 'Shadowfax']

const matches = (o, f) => {
  const paid = o.status === 'CONFIRMED'
  switch (f) {
    case 'TO_SHIP': return paid && (o.fulfillment === 'NEW' || o.fulfillment === 'PACKED')
    case 'SHIPPED': return paid && o.fulfillment === 'SHIPPED'
    case 'DELIVERED': return paid && o.fulfillment === 'DELIVERED'
    case 'CANCELLED': return o.status === 'CANCELLED'
    default: return true
  }
}

// What the merchant can do next for an order, depending on how far it has got
function ShipPanel({ order, onChange }) {
  const [form, setForm] = useState({ courier: order.courier || '', trackingNumber: '', trackingUrl: '', estimatedDelivery: '' })
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const run = async (fn) => {
    setBusy(true); setError('')
    try { onChange((await fn()).data) } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }
  const move = (status, extra = {}) => run(() => api.patch(`/orders/${order.id}/fulfillment`, { status, ...extra }))

  const ship = (e) => {
    e.preventDefault()
    move('SHIPPED', {
      courier: form.courier, trackingNumber: form.trackingNumber, trackingUrl: form.trackingUrl,
      estimatedDelivery: form.estimatedDelivery || null,
    })
  }
  const addUpdate = (e) => {
    e.preventDefault()
    run(() => api.post(`/orders/${order.id}/tracking-updates`, { message: note })).then(() => setNote(''))
  }

  return (
    <div className="ship-panel">
      {order.fulfillment === 'NEW' && (
        <button disabled={busy} onClick={() => move('PACKED')}>Mark packed</button>
      )}

      {order.fulfillment === 'PACKED' && (
        <form onSubmit={ship} className="ship-form">
          <div className="two">
            <label>Courier
              <input list="couriers" value={form.courier} onChange={set('courier')} placeholder="e.g. Delhivery" />
              <datalist id="couriers">{COURIERS.map((c) => <option key={c} value={c} />)}</datalist>
            </label>
            <label>Tracking number<input value={form.trackingNumber} onChange={set('trackingNumber')} /></label>
          </div>
          <div className="two">
            <label>Tracking link (optional)<input type="url" value={form.trackingUrl} onChange={set('trackingUrl')} placeholder="https://…" /></label>
            <label>Expected delivery date<input type="date" value={form.estimatedDelivery} onChange={set('estimatedDelivery')} /></label>
          </div>
          <div><button disabled={busy}>Mark shipped</button></div>
        </form>
      )}

      {order.fulfillment === 'SHIPPED' && (
        <>
          <form onSubmit={addUpdate} className="row">
            <input required maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Add an update, e.g. Out for delivery" aria-label="Tracking update" />
            <button className="ghost" disabled={busy}>Post update</button>
          </form>
          <div><button disabled={busy} onClick={() => move('DELIVERED')}>Mark delivered</button></div>
        </>
      )}
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  )
}

export default function MerchantOrders() {
  const [orders, setOrders] = useState(null)
  const [filter, setFilter] = useState('ALL')
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/orders/merchant').then((r) => setOrders(r.data)).catch((e) => setError(errorMessage(e)))
  }, [])

  const replace = (updated) => setOrders((cur) => cur.map((x) => (x.id === updated.id ? updated : x)))

  if (!orders) return error ? <p className="empty error">{error}</p> : <p className="empty">Loading…</p>

  const term = q.trim().toLowerCase()
  const shown = orders.filter((o) => matches(o, filter) && (!term ||
    [o.id, o.customerName, o.customerEmail, o.customerPhone, o.trackingNumber].some((v) => (v || '').toLowerCase().includes(term))))

  return (
    <div className="dash">
      <p><Link to="/dashboard" className="muted">← Dashboard</Link></p>
      <h1>Orders</h1>
      <div className="toolbar">
        <input type="search" placeholder="Search by customer, order id or tracking number" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search orders" />
      </div>
      <div className="chips" role="tablist">
        {FILTERS.map(([key, label]) => (
          <button key={key} role="tab" aria-selected={filter === key} className={`chip ${filter === key ? 'on' : ''}`} onClick={() => setFilter(key)}>
            {label} ({orders.filter((o) => matches(o, key)).length})
          </button>
        ))}
      </div>
      {shown.length === 0 && <p className="empty">No orders here.</p>}

      {shown.map((o) => {
        const paid = o.status === 'CONFIRMED'
        return (
          <article key={o.id} className="order-card">
            <div className="row">
              <strong>#{o.id.slice(0, 8)} · {o.customerName || 'Customer'}</strong>
              <span className={`status ${o.status}`}>{paid ? fulfillmentLabel[o.fulfillment] : statusLabel[o.status]}</span>
            </div>
            <p className="muted" style={{ margin: 0 }}>
              {new Date(o.createdAt).toLocaleString()} · {o.customerEmail}{o.customerPhone ? ` · ${o.customerPhone}` : ''} · {o.paymentMethod === 'COD' ? 'Cash on delivery' : 'Paid online'}
            </p>
            <p style={{ margin: '.5rem 0' }}>{o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')}</p>
            <p className="muted" style={{ margin: 0 }}>Ship to: {o.shippingAddress}</p>
            {o.failureReason && <p className="error" style={{ margin: 0 }}>{o.failureReason}</p>}

            <div className="row" style={{ marginTop: '.75rem' }}>
              <strong>{money(o.total)}</strong>
              <span className="actions">
                {paid && <button className="ghost" onClick={() => setOpen(open === o.id ? null : o.id)}>{open === o.id ? 'Close' : 'Delivery'}</button>}{' '}
                {paid && <Link to={`/dashboard/orders/${o.id}/invoice`}><button className="ghost" type="button">Invoice</button></Link>}
              </span>
            </div>

            {open === o.id && (
              <>
                {o.fulfillment !== 'DELIVERED' && <ShipPanel order={o} onChange={replace} />}
                <Tracking order={o} />
              </>
            )}
          </article>
        )
      })}
    </div>
  )
}
