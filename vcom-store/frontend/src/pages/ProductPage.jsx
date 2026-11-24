import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { useCart } from '../cart.jsx'
import { useStoreCtx } from '../store.jsx'
import { money } from '../money.js'
import { useStock } from '../stock.js'

export default function ProductPage() {
  const { id } = useParams()
  const { store, base, customer } = useStoreCtx()
  const { add } = useCart()
  const navigate = useNavigate()
  const [p, setP] = useState(null)
  const [error, setError] = useState('')
  const stock = useStock(p ? [p] : [])

  useEffect(() => {
    api.get(`/products/${id}`)
      .then((r) => {
        if (r.data.merchantId !== store.ownerId) setError('This product is not sold by this store')
        else setP(r.data)
      })
      .catch((e) => setError(errorMessage(e)))
  }, [id, store.ownerId])

  if (error) return <p className="empty error">{error} <Link to={base}>Back to {store.name}</Link></p>
  if (!p) return <p className="empty">Loading…</p>

  const left = stock[p.id]
  const out = left === 0
  const addToCart = () => {
    if (!customer) { navigate(`${base}/login`); return }
    add(p).catch((e) => setError(errorMessage(e)))
  }

  return (
    <div className="detail">
      <div className="thumb big">{p.imageUrl ? <img src={p.imageUrl} alt={p.name} /> : <span>{p.name.slice(0, 1)}</span>}</div>
      <div>
        <Link to={base} className="muted">← {store.name}</Link>
        <h1>{p.name}</h1>
        {p.category && <p className="muted">{p.category}</p>}
        <p className="price large">{money(p.price)}</p>
        <p className="muted">{out ? 'Out of stock' : left > 0 ? `${left} in stock` : ''}</p>
        <p className="desc">{p.description || 'No description yet.'}</p>
        <button disabled={out} onClick={addToCart}>{customer ? 'Add to cart' : 'Sign in to buy'}</button>
      </div>
    </div>
  )
}
