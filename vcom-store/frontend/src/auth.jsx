import { createContext, useContext, useState } from 'react'
import api from './api'

const AuthContext = createContext(null)
export const useAuth = () => useContext(AuthContext)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('user')) } catch { return null }
  })

  const store = ({ token, user }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
    setUser(user)
    return user
  }

  // identifier is an email address or a mobile number
  // merchantId set  -> customer of that merchant's store
  // merchantId unset -> merchant account
  const login = async (identifier, password, merchantId) =>
    store((await api.post('/auth/login', { identifier, password, merchantId })).data)

  const register = async (payload) =>
    store((await api.post('/auth/register', payload)).data)

  const logout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setUser(null)
  }

  // owner, store admin (staff) or platform owner
  const isMerchant = ['MERCHANT', 'STORE_ADMIN', 'ADMIN'].includes(user?.role)

  return (
    <AuthContext.Provider value={{ user, isMerchant, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
