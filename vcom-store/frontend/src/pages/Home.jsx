import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { ProductGrid } from '../components.jsx'

export default function Home() {
  const [products, setProducts] = useState([])
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [state, setState] = useState('loading')
  const [error, setError] = useState('')

  useEffect(() => {
    setState('loading')
    const t = setTimeout(() => {
      api.get('/products', { params: { q, category: category || undefined } })
        .then((r) => { setProducts(r.data); setState('ready') })
        .catch((e) => { setError(errorMessage(e)); setState('error') })
    }, 250)
    return () => clearTimeout(t)
  }, [q, category])

  const categories = [...new Set(products.map((p) => p.category).filter(Boolean))]

  return (
    <>
      <section className="toolbar">
        <input type="search" placeholder="Search products" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search products" />
        <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="Category">
          <option value="">All categories</option>
          {categories.map((c) => <option key={c}>{c}</option>)}
          {category && !categories.includes(category) && <option>{category}</option>}
        </select>
      </section>

      {state === 'loading' && <p className="empty">Loading products…</p>}
      {state === 'error' && <p className="empty error">{error}</p>}
      {state === 'ready' && products.length === 0 && (
        <p className="empty">No products found. Merchants can add products from <Link to="/dashboard">My products</Link>.</p>
      )}
      <ProductGrid products={products} />
    </>
  )
}
