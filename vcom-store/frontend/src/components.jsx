import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCart } from './cart.jsx'
import { useStoreCtx } from './store.jsx'
import { errorMessage } from './api'
import { money } from './money.js'
import { useStock } from './stock.js'

export function ProductGrid({ products }) {
  const { add } = useCart()
  const { base, customer } = useStoreCtx()
  const navigate = useNavigate()
  const stock = useStock(products)
  const [error, setError] = useState('')

  const addToCart = (p) => {
    if (!customer) { navigate(`${base}/login`); return }
    add(p).catch((e) => setError(errorMessage(e)))
  }

  return (
    <>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="grid">
        {products.map((p) => {
          const left = stock[p.id]
          const out = left === 0
          return (
            <article key={p.id} className="card">
              <Link to={`${base}/product/${p.id}`} className="thumb">
                {p.imageUrl ? <img src={p.imageUrl} alt={p.name} loading="lazy" /> : <span>{p.name.slice(0, 1)}</span>}
              </Link>
              <div className="card-body">
                <Link to={`${base}/product/${p.id}`} className="title">{p.name}</Link>
                {p.category && <span className="muted">{p.category}</span>}
                {out && <span className="muted">Out of stock</span>}
                {left > 0 && left <= 5 && <span className="muted">Only {left} left</span>}
                <div className="row">
                  <span className="price">{money(p.price)}</span>
                  <button disabled={out} onClick={() => addToCart(p)}>{customer ? 'Add to cart' : 'Sign in to buy'}</button>
                </div>
              </div>
            </article>
          )
        })}
      </div>
    </>
  )
}
