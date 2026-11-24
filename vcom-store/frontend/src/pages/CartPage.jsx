import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { useCart } from '../cart.jsx'
import { useStoreCtx } from '../store.jsx'
import { money } from '../money.js'

export default function CartPage() {
  const { items, setQty, total, clear } = useCart()
  const { base, customer } = useStoreCtx()
  const navigate = useNavigate()
  const [address, setAddress] = useState('')
  const [method, setMethod] = useState('ONLINE')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (!customer) return <p className="empty"><Link to={`${base}/login`}>Sign in</Link> to see your cart.</p>
  if (items.length === 0) return <p className="empty">Your cart is empty. <Link to={base}>Find something to buy</Link></p>

  const change = (id, qty) => setQty(id, qty).catch((e) => setError(errorMessage(e)))

  const placeOrder = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      const r = await api.post('/orders', {
        items: items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
        shippingAddress: address,
        paymentMethod: method,
      })
      navigate(`${base}/orders/${r.data.id}`)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <div className="cart">
      <h1>Your cart</h1>
      {items.map((i) => (
        <div key={i.productId} className="cart-row">
          <Link to={`${base}/product/${i.productId}`} className="title">{i.name}</Link>
          <div className="qty">
            <button className="ghost" onClick={() => change(i.productId, i.quantity - 1)} aria-label={`Remove one ${i.name}`}>−</button>
            <span>{i.quantity}</span>
            <button className="ghost" onClick={() => change(i.productId, i.quantity + 1)} aria-label={`Add one ${i.name}`}>+</button>
          </div>
          <span className="price">{money(i.price * i.quantity)}</span>
        </div>
      ))}
      <div className="cart-total">
        <button className="link" onClick={() => clear().catch((e) => setError(errorMessage(e)))}>Empty cart</button>
        <strong>Total {money(total)}</strong>
      </div>

      <form className="panel wide" onSubmit={placeOrder}>
        <h2 style={{ margin: 0 }}>Checkout</h2>
        <label>Delivery address
          <textarea required rows="3" value={address} onChange={(e) => setAddress(e.target.value)} />
        </label>
        <label>Payment
          <select value={method} onChange={(e) => setMethod(e.target.value)}>
            <option value="ONLINE">Pay online (UPI, cards, net banking)</option>
            <option value="COD">Cash on delivery</option>
            {import.meta.env.DEV && <option value="TEST_DECLINE">Development only: payment that fails</option>}
          </select>
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button disabled={busy}>{busy ? 'Placing order…' : `Place order · ${money(total)}`}</button>
      </form>
    </div>
  )
}
