import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { useCart } from '../cart.jsx'
import { useStoreCtx } from '../store.jsx'
import { openCheckout, toVerifyBody } from '../razorpay.js'
import { money } from '../money.js'
import Tracking from './Tracking.jsx'

export const statusLabel = {
  PENDING: 'Checking stock',
  STOCK_RESERVED: 'Waiting for payment',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
}

export const fulfillmentLabel = {
  NEW: 'Preparing',
  PACKED: 'Packed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
}

export default function OrderPage() {
  const { id } = useParams()
  const { base, customer, store } = useStoreCtx()
  const { refresh } = useCart()
  const [order, setOrder] = useState(null)
  const [error, setError] = useState('')
  const [details, setDetails] = useState(null)               // Razorpay Checkout details, once the server has them
  const [pay, setPay] = useState({ state: 'idle', message: '' })   // idle | opening | verifying | dismissed | error
  const detailsRef = useRef(null)
  const autoOpened = useRef(false)

  // The order moves through Kafka-driven steps, so poll until it reaches a final state.
  useEffect(() => {
    if (!customer) return
    let timer
    let stopped = false
    const load = async () => {
      try {
        const o = (await api.get(`/orders/${id}`)).data
        if (stopped) return
        setOrder(o)
        if (o.status === 'STOCK_RESERVED' && o.paymentMethod === 'ONLINE' && !detailsRef.current) {
          try {
            const d = (await api.get(`/payments/order/${id}/checkout`)).data
            detailsRef.current = d
            if (!stopped) setDetails(d)
          } catch { /* the payment is still being prepared; the next poll retries */ }
        }
        if (o.status === 'PENDING' || o.status === 'STOCK_RESERVED') timer = setTimeout(load, 1500)
        else {
          refresh()
          // keep the tracking fresh until the parcel arrives
          if (o.status === 'CONFIRMED' && o.fulfillment !== 'DELIVERED') timer = setTimeout(load, 30000)
        }
      } catch (e) {
        if (!stopped) setError(errorMessage(e))
      }
    }
    load()
    return () => { stopped = true; clearTimeout(timer) }
  }, [id, refresh, customer])

  const startPayment = (d = details) => {
    if (!d || !order) return
    setPay({ state: 'opening', message: '' })
    openCheckout({
      details: d,
      name: store.name,
      description: `Order ${order.id.slice(0, 8)}`,
      prefill: { name: customer.name, email: customer.email, contact: customer.phone || undefined },
      color: store.themeColor,
      onSuccess: async (resp) => {
        setPay({ state: 'verifying', message: '' })
        try {
          await api.post(`/payments/order/${id}/verify`, toVerifyBody(resp))
          // the order flips to Confirmed through Kafka; polling picks it up
        } catch (e) {
          setPay({ state: 'error', message: errorMessage(e) })
        }
      },
      onDismiss: () => setPay((p) => (p.state === 'verifying' ? p : { state: 'dismissed', message: '' })),
    }).catch((e) => setPay({ state: 'error', message: e.message }))
  }

  // open Checkout automatically the first time it is ready
  useEffect(() => {
    if (details && !autoOpened.current) { autoOpened.current = true; startPayment(details) }
  }, [details]) // eslint-disable-line react-hooks/exhaustive-deps

  const cancelOrder = async () => {
    try { await api.post(`/payments/order/${id}/abandon`) } catch (e) { setPay({ state: 'error', message: errorMessage(e) }) }
  }

  if (!customer) return <p className="empty"><Link to={`${base}/login`}>Sign in</Link> to see this order.</p>
  if (error) return <p className="empty error">{error}</p>
  if (!order) return <p className="empty">Loading…</p>

  const waitingToPay = order.status === 'STOCK_RESERVED' && order.paymentMethod === 'ONLINE'
  const steps = ['PENDING', 'STOCK_RESERVED', 'CONFIRMED']
  const reached = steps.indexOf(order.status)

  return (
    <div className="cart">
      <Link to={`${base}/orders`} className="muted">← All orders</Link>
      <h1>Order {order.id.slice(0, 8)}</h1>
      <p className={`status big ${order.status}`} role="status">{statusLabel[order.status]}</p>

      {waitingToPay && (
        <div className="panel wide">
          <h2 style={{ margin: 0 }}>Complete your payment</h2>
          <p className="muted" style={{ margin: 0 }}>Your items are held for 15 minutes. Pay with UPI, card or net banking.</p>
          {pay.state === 'verifying' ? (
            <p role="status">Confirming your payment…</p>
          ) : (
            <div className="row" style={{ justifyContent: 'flex-start' }}>
              <button onClick={() => startPayment()} disabled={!details || pay.state === 'opening'}>
                {details ? `Pay ${money(order.total)}` : 'Preparing payment…'}
              </button>
              <button className="ghost" onClick={cancelOrder}>Cancel order</button>
            </div>
          )}
          {pay.state === 'dismissed' && <p className="muted" style={{ margin: 0 }}>The payment wasn’t completed. You can try again.</p>}
          {pay.state === 'error' && <p className="error" role="alert" style={{ margin: 0 }}>{pay.message}</p>}
        </div>
      )}

      {order.status === 'CONFIRMED' && (
        <p><Link to={`${base}/orders/${order.id}/invoice`}>View invoice</Link></p>
      )}
      {(order.status === 'CONFIRMED' || order.status === 'CANCELLED') && <Tracking order={order} />}

      {order.status === 'CANCELLED' ? (
        <p className="error">{order.failureReason || 'This order was cancelled.'} If you were charged, please contact the store.</p>
      ) : order.status !== 'CONFIRMED' && !waitingToPay && (
        <ol className="steps">
          {['Order placed', 'Stock reserved', 'Payment received'].map((s, i) => (
            <li key={s} className={i <= reached ? 'done' : ''}>{s}</li>
          ))}
        </ol>
      )}

      {order.items.map((i) => (
        <div key={i.productId} className="cart-row">
          <span>{i.name}</span>
          <span className="muted">× {i.quantity}</span>
          <span className="price">{money(i.price * i.quantity)}</span>
        </div>
      ))}
      <div className="cart-total">
        <span className="muted">{order.paymentMethod === 'COD' ? 'Cash on delivery' : 'Pay online'}</span>
        <strong>Total {money(order.total)}</strong>
      </div>
      <p className="muted">Delivering to: {order.shippingAddress}</p>
    </div>
  )
}
