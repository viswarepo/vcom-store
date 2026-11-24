import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { money } from '../money.js'
import { useAuth } from '../auth.jsx'
import { useStock } from '../stock.js'
import ProductFilters, { priceError } from '../ProductFilters.jsx'
import Staff from './Staff.jsx'
import MerchantBilling from './MerchantBilling.jsx'
import { statusLabel } from './OrderPage.jsx'

const blank = { name: '', description: '', price: '', imageUrl: '', category: '', stock: 0 }

export default function Dashboard() {
  const [products, setProducts] = useState([])
  const [orders, setOrders] = useState([])
  const [form, setForm] = useState(blank)
  const [editingId, setEditingId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [reload, setReload] = useState(0)
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [sort, setSort] = useState('latest')
  const [categories, setCategories] = useState([])
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [priceRange, setPriceRange] = useState(null)
  const { user } = useAuth()
  const isOwner = user?.role === 'MERCHANT' || user?.role === 'ADMIN'
  const [hasStore, setHasStore] = useState(null)
  const [store, setStore] = useState(null)
  const [customers, setCustomers] = useState([])
  const stock = useStock(products, reload)

  const load = () => {
    api.get('/products/mine', { params: { q, category: category || undefined, sort,
      minPrice: minPrice === '' ? undefined : minPrice, maxPrice: maxPrice === '' ? undefined : maxPrice } })
      .then((r) => setProducts(r.data)).catch((e) => setError(errorMessage(e)))
    if (user?.tenantId) api.get('/products/categories', { params: { merchantId: user.tenantId } }).then((r) => setCategories(r.data)).catch(() => {})
    if (user?.tenantId) api.get('/products/price-range', { params: { merchantId: user.tenantId } }).then((r) => setPriceRange(r.data)).catch(() => {})
    setReload((n) => n + 1)
  }
  // reload when the search, category, price range or sort changes (typing is debounced)
  useEffect(() => {
    if (priceError(minPrice, maxPrice)) return   // the filter bar explains the problem
    const t = setTimeout(load, q || minPrice !== '' || maxPrice !== '' ? 250 : 0)
    return () => clearTimeout(t)
  }, [q, category, minPrice, maxPrice, sort]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    api.get('/stores/mine').then((r) => { setHasStore(true); setStore(r.data) }).catch((e) => setHasStore(e.response?.status === 404 ? false : true))
  }, [])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const save = async (e) => {
    e.preventDefault()
    setError(''); setNotice('')
    try {
      const { stock: qty, ...fields } = form
      const body = { ...fields, price: Number(fields.price) }
      const saved = editingId ? (await api.put(`/products/${editingId}`, body)).data : (await api.post('/products', body)).data
      await api.put(`/inventory/${saved.id}`, { quantity: Number(qty) })
      setNotice(editingId ? 'Product saved' : 'Product added')
      setForm(blank); setEditingId(null); load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const edit = (p) => {
    setEditingId(p.id)
    setForm({ name: p.name, description: p.description || '', price: p.price, imageUrl: p.imageUrl || '', category: p.category || '', stock: stock[p.id] ?? 0 })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const remove = async (p) => {
    if (!window.confirm(`Delete “${p.name}”?`)) return
    try { await api.delete(`/products/${p.id}`); setNotice('Product deleted'); load() }
    catch (err) { setError(errorMessage(err)) }
  }

  return (
    <div className="dash">
      <p>
        {isOwner && <><Link to="/dashboard/store">Store settings</Link> · </>}
        {store && <>Your storefront: <Link to={`/store/${store.slug}`}>/store/{store.slug}</Link></>}
      </p>
      {store && <p className="muted">Share this link with your customers. They create their own account on your store and can only shop your products.</p>}
      {hasStore === false && (
        <p className="notice">Open your store first so customers can find your products. {isOwner ? <Link to="/merchant/onboarding">Choose a plan and open your store</Link> : 'Ask the store owner to create it.'}</p>
      )}
      <nav className="quick">
        <Link to="/dashboard/orders">Orders</Link>
        <Link to="/dashboard/customers">Customers</Link>
        <Link to="/dashboard/invoices">Invoices</Link>
        {isOwner && <Link to="/dashboard/reports">Sales reports</Link>}
      </nav>
      {store && isOwner && <MerchantBilling store={store} />}
      <form className="panel wide" onSubmit={save}>
        <h1>{editingId ? 'Edit product' : 'Add a product'}</h1>
        <label>Name<input required value={form.name} onChange={set('name')} /></label>
        <div className="two">
          <label>Price (INR)<input required type="number" min="0" step="0.01" value={form.price} onChange={set('price')} /></label>
          <label>Units in stock<input required type="number" min="0" step="1" value={form.stock} onChange={set('stock')} /></label>
        </div>
        <label>Category<input value={form.category} onChange={set('category')} /></label>
        <label>Image URL<input type="url" value={form.imageUrl} onChange={set('imageUrl')} /></label>
        <label>Description<textarea rows="3" value={form.description} onChange={set('description')} /></label>
        {error && <p className="error" role="alert">{error}</p>}
        {notice && <p className="ok" role="status">{notice}</p>}
        <div className="row">
          <button>{editingId ? 'Save changes' : 'Add product'}</button>
          {editingId && <button type="button" className="ghost" onClick={() => { setEditingId(null); setForm(blank) }}>Cancel</button>}
        </div>
      </form>

      <h2>Your products ({products.length})</h2>
      <ProductFilters q={q} setQ={setQ} category={category} setCategory={setCategory} sort={sort} setSort={setSort}
        minPrice={minPrice} setMinPrice={setMinPrice} maxPrice={maxPrice} setMaxPrice={setMaxPrice}
        categories={categories} priceRange={priceRange} placeholder="Search your products" />
      {products.length === 0 && (q || category || minPrice !== '' || maxPrice !== ''
        ? <p className="empty">No products match your search.</p>
        : <p className="empty">You haven’t added any products yet.</p>)}
      <table className="table">
        <tbody>
          {products.map((p) => (
            <tr key={p.id}>
              <td>{p.name}</td>
              <td className="muted">{p.category}</td>
              <td className="muted">{stock[p.id] ?? '…'} in stock</td>
              <td className="price">{money(p.price)}</td>
              <td className="actions">
                <button className="ghost" onClick={() => edit(p)}>Edit</button>
                <button className="ghost danger" onClick={() => remove(p)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {user?.role === 'MERCHANT' && <Staff />}
    </div>
  )
}
