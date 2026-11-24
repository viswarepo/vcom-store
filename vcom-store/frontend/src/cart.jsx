import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import api from './api'
import { useAuth } from './auth.jsx'

// Cart lives in cart-service (Redis). Signing in is required to add items.
const CartContext = createContext(null)
export const useCart = () => useContext(CartContext)

export function CartProvider({ children }) {
  const { user } = useAuth()
  const [items, setItems] = useState([])

  const refresh = useCallback(async () => {
    if (!user) { setItems([]); return }
    try { setItems((await api.get('/cart')).data) } catch { /* keep current items */ }
  }, [user])

  useEffect(() => { refresh() }, [refresh])

  const add = async (p) => {
    if (!user) return
    const r = await api.post('/cart/items', {
      productId: p.id, name: p.name, price: p.price, imageUrl: p.imageUrl, quantity: 1,
    })
    setItems(r.data)
  }

  const setQty = async (productId, quantity) => {
    const r = quantity <= 0
      ? await api.delete(`/cart/items/${productId}`)
      : await api.put(`/cart/items/${productId}`, { quantity })
    setItems(r.data)
  }

  const clear = async () => { await api.delete('/cart'); setItems([]) }

  const count = items.reduce((n, i) => n + i.quantity, 0)
  const total = items.reduce((n, i) => n + i.quantity * Number(i.price), 0)

  return (
    <CartContext.Provider value={{ items, add, setQty, clear, refresh, count, total }}>
      {children}
    </CartContext.Provider>
  )
}
