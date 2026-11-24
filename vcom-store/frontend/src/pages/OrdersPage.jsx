import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { money } from '../money.js'
import { useStoreCtx } from '../store.jsx'
import { statusLabel, fulfillmentLabel } from './OrderPage.jsx'

export default function OrdersPage() {
  const { base, customer } = useStoreCtx()
  const [orders, setOrders] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!customer) return
    api.get('/orders').then((r) => setOrders(r.data)).catch((e) => setError(errorMessage(e)))
  }, [customer])

  if (!customer) return <p className="empty"><Link to={`${base}/login`}>Sign in</Link> to see your orders.</p>
  if (error) return <p className="empty error">{error}</p>
  if (!orders) return <p className="empty">Loading…</p>
  if (orders.length === 0) return <p className="empty">You haven’t placed any orders yet. <Link to={base}>Start shopping</Link></p>

  return (
    <div className="cart">
      <h1>Your orders</h1>
      {orders.map((o) => (
        <Link key={o.id} to={`${base}/orders/${o.id}`} className="order-row">
          <span>
            <strong>{o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')}</strong>
            <span className="muted block">{new Date(o.createdAt).toLocaleString()}</span>
          </span>
          <span className={`status ${o.status}`}>{o.status === 'CONFIRMED' ? fulfillmentLabel[o.fulfillment] : statusLabel[o.status]}</span>
          <span className="price">{money(o.total)}</span>
        </Link>
      ))}
    </div>
  )
}
