import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export const errorMessage = (e) =>
  e.response?.data?.message || (e.response?.status === 401 ? 'Please sign in again' : 'Something went wrong. Try again.')

export default api
