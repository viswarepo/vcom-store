import { useEffect, useState } from 'react'
import api from './api'

// { productId: quantity } for the given products (missing = not loaded yet)
export function useStock(products, reloadKey = 0) {
  const [stock, setStock] = useState({})
  useEffect(() => {
    if (!products.length) { setStock({}); return }
    api.get('/inventory', { params: { ids: products.map((p) => p.id).join(',') } })
      .then((r) => setStock(r.data))
      .catch(() => {})
  }, [products, reloadKey])
  return stock
}
