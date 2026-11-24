import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { useAuth } from '../auth.jsx'
import PlanCards from '../PlanCards.jsx'
import { openCheckout, toVerifyBody } from '../razorpay.js'
import { limitLabel, periodLabel, priceLabel } from '../plans.js'
import { money } from '../money.js'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const blank = { name: '', description: '', address: '', gstin: '', phone: '' }

// Step 1: choose a plan.  Step 2: store and billing details, then pay and open the store.
// Also used later to renew or change plan.
export default function Onboarding() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [plans, setPlans] = useState(null)
  const [planId, setPlanId] = useState(null)
  const [step, setStep] = useState(1)
  const [store, setStore] = useState(null)    // the merchant's store if it already exists
  const [sub, setSub] = useState(null)
  const [form, setForm] = useState(blank)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.get('/payments/plans').then((r) => setPlans(r.data)).catch((e) => setError(errorMessage(e)))
    api.get('/payments/subscription').then((r) => setSub(r.data)).catch(() => {})
    api.get('/stores/mine')
      .then((r) => { setStore(r.data); setForm({ ...blank, name: r.data.name, description: r.data.description || '', address: r.data.address || '', gstin: r.data.gstin || '' }) })
      .catch(() => {})
  }, [])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const plan = plans?.find((p) => p.id === planId)
  const paid = plan && Number(plan.price) > 0

  const waitForOpen = async () => {
    for (let i = 0; i < 10; i++) {
      await sleep(1000)
      const s = (await api.get('/stores/mine')).data
      if (s.active) break
    }
    navigate('/dashboard')
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      // 1. save the store details (also what gets printed on invoices)
      const body = { name: form.name, description: form.description, address: form.address, gstin: form.gstin,
        logoUrl: store?.logoUrl, themeColor: store?.themeColor }
      const saved = store ? (await api.put('/stores/mine', body)).data : (await api.post('/stores', body)).data
      setStore(saved)

      // 2. pick the plan: free plans open the store at once, paid plans return Razorpay Checkout details
      const r = (await api.post('/payments/subscription/checkout', { planId })).data
      if (!r.checkout) { await waitForOpen(); return }

      await openCheckout({
        details: r.checkout,
        name: 'vcom-store',
        description: `${plan.name} plan`,
        prefill: { name: user?.name, email: user?.email, contact: form.phone || user?.phone || undefined },
        onSuccess: async (resp) => {
          try {
            await api.post('/payments/subscription/verify', toVerifyBody(resp))
            await waitForOpen()
          } catch (err) {
            setError(errorMessage(err)); setBusy(false)
          }
        },
        onDismiss: () => setBusy(false),
      })
    } catch (err) {
      setError(err.response ? errorMessage(err) : err.message)
      setBusy(false)
    }
  }

  if (!plans) return error ? <p className="empty error">{error}</p> : <p className="empty">Loading plans…</p>
  if (plans.length === 0) return <p className="empty">No plans are available yet. Please check back soon.</p>

  return (
    <div className="onboarding">
      <p className="muted">Step {step} of 2</p>

      {step === 1 && (
        <>
          <h1>{sub?.active ? 'Renew or change your plan' : 'Choose a plan for your store'}</h1>
          {sub?.active && (
            <p className="notice">
              You are on <strong>{sub.planName}</strong>
              {sub.currentPeriodEnd ? <>, paid until {new Date(sub.currentPeriodEnd).toLocaleDateString()}</> : ''}. Renewing the same plan adds a new period to the end of this one.
            </p>
          )}
          <PlanCards plans={plans} selectedId={planId} onSelect={setPlanId} />
          <div>
            <button disabled={!plan} onClick={() => setStep(2)}>Continue with {plan ? plan.name : 'a plan'}</button>
          </div>
        </>
      )}

      {step === 2 && plan && (
        <>
          <h1>Your details and payment</h1>
          <div className="checkout-grid">
            <form className="panel wide" onSubmit={submit}>
              <h2 style={{ margin: 0 }}>Store details</h2>
              <label>Store name<input required value={form.name} onChange={set('name')} /></label>
              <label>Short description<textarea rows="2" value={form.description} onChange={set('description')} /></label>
              <label>Business address (printed on invoices)<textarea required rows="3" value={form.address} onChange={set('address')} /></label>
              <div className="two">
                <label>GSTIN or tax id (optional)<input maxLength={20} value={form.gstin} onChange={set('gstin')} /></label>
                <label>Mobile number (for payment)<input type="tel" value={form.phone} onChange={set('phone')} /></label>
              </div>
              {error && <p className="error" role="alert">{error}</p>}
              <div className="row" style={{ justifyContent: 'flex-start' }}>
                <button type="button" className="ghost" onClick={() => setStep(1)} disabled={busy}>Back</button>
                <button disabled={busy}>{busy ? 'Working…' : paid ? `Pay ${money(plan.price)} and open my store` : 'Open my store'}</button>
              </div>
            </form>

            <aside className="panel summary" aria-label="Order summary">
              <h2 style={{ margin: 0 }}>Order summary</h2>
              <div className="row"><span>{plan.name} plan</span><strong>{priceLabel(plan)}</strong></div>
              <ul className="muted">
                <li>{Number(plan.commissionPercent)}% commission on each sale</li>
                <li>{limitLabel(plan.maxProducts)}</li>
              </ul>
              <div className="row"><span>Due today</span><strong>{money(plan.price)}</strong></div>
              {paid ? (
                <p className="muted" style={{ margin: 0 }}>
                  Valid for one {periodLabel[plan.billingPeriod]}. It does not renew by itself: when the period ends your store
                  closes to customers until you renew from your dashboard.
                </p>
              ) : (
                <p className="muted" style={{ margin: 0 }}>No payment needed. You can upgrade at any time.</p>
              )}
              <p className="muted" style={{ margin: 0 }}>Payments are processed securely by Razorpay.</p>
            </aside>
          </div>
        </>
      )}
      {step === 2 && !plan && <p className="empty">Choose a plan first. <button className="link" onClick={() => setStep(1)}>Back to plans</button></p>}
      <p className="muted"><Link to="/dashboard">Skip for now</Link>: you can prepare your products, but the store stays closed to customers until a plan is active.</p>
    </div>
  )
}
