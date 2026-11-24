import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'

export default function Stores() {
  const [stores, setStores] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/stores').then((r) => setStores(r.data)).catch((e) => setError(errorMessage(e)))
  }, [])

  if (error) return <p className="empty error">{error}</p>
  if (!stores) return <p className="empty">Loading…</p>
  if (stores.length === 0) return <p className="empty">No stores yet. <Link to="/merchant/register">Open the first one</Link>.</p>

  return (
    <>
      <h2>Browse stores</h2>
      <div className="grid">
        {stores.map((s) => (
          <Link key={s.id} to={`/store/${s.slug}`} className="card store-card" style={{ borderTopColor: s.themeColor }}>
            <div className="card-body">
              <span className="title">{s.name}</span>
              <span className="muted">{s.description}</span>
            </div>
          </Link>
        ))}
      </div>
    </>
  )
}
