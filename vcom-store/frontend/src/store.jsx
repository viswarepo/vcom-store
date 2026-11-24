import { createContext, useContext, useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useParams } from 'react-router-dom'
import api, { errorMessage } from './api'
import { useAuth } from './auth.jsx'
import { useCart } from './cart.jsx'

// Everything under /store/:slug runs inside one merchant's store.
export const StoreContext = createContext(null)
export const useStoreCtx = () => useContext(StoreContext)

export function StoreLayout() {
  const { slug } = useParams()
  const { user, logout } = useAuth()
  const { count } = useCart()
  const [store, setStore] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    setStore(null); setError('')
    api.get(`/stores/slug/${slug}`).then((r) => setStore(r.data)).catch((e) => setError(errorMessage(e)))
  }, [slug])

  if (error) return <main className="page"><p className="empty error">{error} <Link to="/">Back to vcom-store</Link></p></main>
  if (!store) return <main className="page"><p className="empty">Loading…</p></main>

  const base = `/store/${slug}`
  // A customer session only counts inside the store the customer signed up to
  const customer = user && user.role === 'CUSTOMER' && user.tenantId === store.ownerId ? user : null
  const elsewhere = user && !customer
  const isOwner = user && (user.id === store.ownerId || (user.role === 'STORE_ADMIN' && user.tenantId === store.ownerId))

  // Customers can't see a store until the merchant has been activated by the platform
  if (!store.active && !isOwner) {
    return (
      <main className="page">
        <div className="empty">
          <h1>{store.name}</h1>
          <p>This store isn’t open yet. Please check back soon.</p>
          <Link to="/">Back to vcom-store</Link>
        </div>
      </main>
    )
  }

  return (
    <StoreContext.Provider value={{ store, base, customer }}>
      <div style={{ '--accent': store.themeColor }}>
        <header className="header">
          <Link to={base} className="brand store-brand">
            {store.logoUrl && <img src={store.logoUrl} alt="" />}{store.name}
          </Link>
          <nav>
            <NavLink to={`${base}/cart`}>Cart{customer && count > 0 && <span className="badge">{count}</span>}</NavLink>
            {customer && <NavLink to={`${base}/orders`}>Orders</NavLink>}
            {customer && <NavLink to={`${base}/account/password`}>Password</NavLink>}
            {customer ? (
              <button className="link" onClick={logout}>Sign out ({customer.name})</button>
            ) : (
              <>
                <NavLink to={`${base}/login`}>Sign in</NavLink>
                <NavLink to={`${base}/register`}>Create account</NavLink>
              </>
            )}
          </nav>
        </header>
        <main className="page">
          {elsewhere && (
            <p className="notice">
              You’re signed in as {user.name} ({user.role === 'CUSTOMER' ? 'a customer of another store' : 'a merchant'}).{' '}
              <button className="link" onClick={logout}>Sign out</button> to shop at {store.name}.
            </p>
          )}
          {!store.active && isOwner && (
            <p className="notice">Only you can see this store. <Link to="/dashboard">Activate it</Link> to open it to customers.</p>
          )}
          <Outlet />
        </main>
        <footer className="footer">{store.name} · powered by <Link to="/">vcom-store</Link></footer>
      </div>
    </StoreContext.Provider>
  )
}
