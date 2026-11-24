import { useEffect, useState } from 'react'
import api, { errorMessage } from '../api'
import { ProductGrid } from '../components.jsx'
import ProductFilters, { priceError } from '../ProductFilters.jsx'
import { useStoreCtx } from '../store.jsx'

export default function StoreFront() {
  const { store } = useStoreCtx()
  const [products, setProducts] = useState([])
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [sort, setSort] = useState('latest')
  const [state, setState] = useState('loading')
  const [error, setError] = useState('')
  const [categories, setCategories] = useState([])
  const [priceRange, setPriceRange] = useState(null)

  useEffect(() => {
    api.get('/products/categories', { params: { merchantId: store.ownerId } }).then((r) => setCategories(r.data)).catch(() => {})
    api.get('/products/price-range', { params: { merchantId: store.ownerId } }).then((r) => setPriceRange(r.data)).catch(() => {})
  }, [store.ownerId])

  useEffect(() => {
    if (priceError(minPrice, maxPrice)) return   // the filter bar explains the problem
    setState('loading')
    const t = setTimeout(() => {
      api.get('/products', {
        params: { merchantId: store.ownerId, q, category: category || undefined, sort,
          minPrice: minPrice === '' ? undefined : minPrice, maxPrice: maxPrice === '' ? undefined : maxPrice },
      })
        .then((r) => { setProducts(r.data); setState('ready') })
        .catch((e) => { setError(errorMessage(e)); setState('error') })
    }, q || minPrice !== '' || maxPrice !== '' ? 250 : 0)
    return () => clearTimeout(t)
  }, [store.ownerId, q, category, minPrice, maxPrice, sort])

  return (
    <>
      <section className="banner" style={{ background: store.themeColor }}>
        {store.logoUrl && <img src={store.logoUrl} alt="" />}
        <div>
          <h1>{store.name}</h1>
          <p>{store.description}</p>
        </div>
      </section>

      <ProductFilters q={q} setQ={setQ} category={category} setCategory={setCategory} sort={sort} setSort={setSort}
        minPrice={minPrice} setMinPrice={setMinPrice} maxPrice={maxPrice} setMaxPrice={setMaxPrice}
        categories={categories} priceRange={priceRange} placeholder={`Search ${store.name}`} />

      {state === 'loading' && <p className="empty">Loading products…</p>}
      {state === 'error' && <p className="empty error">{error}</p>}
      {state === 'ready' && products.length === 0 && <p className="empty">No products found.</p>}
      <ProductGrid products={products} />
    </>
  )
}
