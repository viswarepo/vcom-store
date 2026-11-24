import { Link, NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import { StoreLayout } from './store.jsx'
import Landing from './pages/Landing.jsx'
import AuthPage from './pages/AuthPage.jsx'
import ForgotPassword from './pages/ForgotPassword.jsx'
import ChangePassword from './pages/ChangePassword.jsx'
import Dashboard from './pages/Dashboard.jsx'
import AdminDashboard from './pages/AdminDashboard.jsx'
import MerchantOrders from './pages/MerchantOrders.jsx'
import MerchantCustomers from './pages/MerchantCustomers.jsx'
import MerchantInvoices from './pages/MerchantInvoices.jsx'
import InvoiceView from './pages/InvoiceView.jsx'
import Onboarding from './pages/Onboarding.jsx'
import MerchantReports from './pages/MerchantReports.jsx'
import AdminReports from './pages/AdminReports.jsx'
import StoreSettings from './pages/StoreSettings.jsx'
import StoreFront from './pages/StoreFront.jsx'
import ProductPage from './pages/ProductPage.jsx'
import CartPage from './pages/CartPage.jsx'
import OrdersPage from './pages/OrdersPage.jsx'
import OrderPage from './pages/OrderPage.jsx'

function PlatformLayout() {
  const { user, isMerchant, logout } = useAuth()
  return (
    <>
      <header className="header">
        <Link to="/" className="brand">vcom-store</Link>
        <nav>
          {user?.role === 'ADMIN' ? (
            <NavLink to="/admin">Platform owner</NavLink>
          ) : isMerchant ? (
            <NavLink to="/dashboard" end>Dashboard</NavLink>
          ) : (
            <>
              <NavLink to="/merchant/login">Merchant sign in</NavLink>
              <NavLink to="/merchant/register">Sell on vcom-store</NavLink>
            </>
          )}
          {user && <NavLink to="/account/password">Change password</NavLink>}
          {user && <button className="link" onClick={logout}>Sign out ({user.name})</button>}
        </nav>
      </header>
      <main className="page"><Outlet /></main>
    </>
  )
}

function RequireMerchant({ children }) {
  const { isMerchant } = useAuth()
  return isMerchant ? children : <Navigate to="/merchant/login" replace />
}

function RequireOwner({ children }) {
  const { user } = useAuth()
  return user?.role === 'MERCHANT' || user?.role === 'ADMIN' ? children : <Navigate to="/dashboard" replace />
}

function RequireAdmin({ children }) {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? children : <Navigate to="/merchant/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route element={<PlatformLayout />}>
        <Route path="/" element={<Landing />} />
        <Route path="/merchant/login" element={<AuthPage scope="merchant" mode="login" />} />
        <Route path="/merchant/register" element={<AuthPage scope="merchant" mode="register" />} />
        <Route path="/merchant/forgot-password" element={<ForgotPassword scope="merchant" />} />
        <Route path="/account/password" element={<ChangePassword />} />
        <Route path="/merchant/onboarding" element={<RequireMerchant><RequireOwner><Onboarding /></RequireOwner></RequireMerchant>} />
        <Route path="/admin/reports" element={<RequireAdmin><AdminReports /></RequireAdmin>} />
        <Route path="/dashboard/reports" element={<RequireMerchant><RequireOwner><MerchantReports /></RequireOwner></RequireMerchant>} />
        <Route path="/admin" element={<RequireAdmin><AdminDashboard /></RequireAdmin>} />
        <Route path="/dashboard/orders" element={<RequireMerchant><MerchantOrders /></RequireMerchant>} />
        <Route path="/dashboard/orders/:id/invoice" element={<RequireMerchant><InvoiceView /></RequireMerchant>} />
        <Route path="/dashboard/customers" element={<RequireMerchant><MerchantCustomers /></RequireMerchant>} />
        <Route path="/dashboard/invoices" element={<RequireMerchant><MerchantInvoices /></RequireMerchant>} />
        <Route path="/dashboard" element={<RequireMerchant><Dashboard /></RequireMerchant>} />
        <Route path="/dashboard/store" element={<RequireMerchant><RequireOwner><StoreSettings /></RequireOwner></RequireMerchant>} />
        <Route path="*" element={<p className="empty">That page doesn’t exist. <Link to="/">Back to vcom-store</Link></p>} />
      </Route>

      <Route path="/store/:slug" element={<StoreLayout />}>
        <Route index element={<StoreFront />} />
        <Route path="product/:id" element={<ProductPage />} />
        <Route path="cart" element={<CartPage />} />
        <Route path="login" element={<AuthPage scope="store" mode="login" />} />
        <Route path="register" element={<AuthPage scope="store" mode="register" />} />
        <Route path="forgot-password" element={<ForgotPassword scope="store" />} />
        <Route path="account/password" element={<ChangePassword />} />
        <Route path="orders" element={<OrdersPage />} />
        <Route path="orders/:id" element={<OrderPage />} />
        <Route path="orders/:id/invoice" element={<InvoiceView />} />
      </Route>
    </Routes>
  )
}
