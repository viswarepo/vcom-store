import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api'
import { limitLabel, priceLabel } from '../plans.js'
import { money } from '../money.js'

// The merchant's plan with the platform owner, and their earnings.
export default function MerchantBilling({ store }) {
  const [sub, setSub] = useState(null)
  const [sum, setSum] = useState(null)

  useEffect(() => {
    api.get('/payments/subscription').then((r) => setSub(r.data)).catch(() => {})
    api.get('/payments/merchant/summary').then((r) => setSum(r.data)).catch(() => {})
  }, [store.active])

  const ends = sub?.currentPeriodEnd ? new Date(sub.currentPeriodEnd) : null
  const soon = ends && ends - Date.now() < 7 * 24 * 3600 * 1000

  return (
    <section className="panel wide">
      <h2 style={{ margin: 0 }}>Your plan</h2>
      {sub && sub.status === 'NONE' && (
        <p style={{ margin: 0 }}>You haven’t chosen a plan yet, so your store is closed to customers.</p>
      )}
      {sub && sub.status !== 'NONE' && (
        <>
          <p style={{ margin: 0 }}>
            <strong>{sub.planName}</strong> · {priceLabel(sub)} · {Number(sub.commissionPercent)}% commission · {limitLabel(sub.maxProducts)}
          </p>
          <p style={{ margin: 0 }}>
            Status: <span className={`status ${sub.active ? 'CONFIRMED' : 'CANCELLED'}`}>{sub.active ? 'Active' : 'Expired'}</span>
            {ends && <span className="muted"> · {sub.active ? 'renews by' : 'ended'} {ends.toLocaleDateString()}</span>}
          </p>
        </>
      )}
      {sub && (sub.status === 'NONE' || !sub.active || soon) && (
        <p className="notice" style={{ margin: 0 }}>
          {sub.status === 'NONE' ? 'Choose a plan to open your store.' : sub.active ? 'Your plan ends soon. Renew to keep your store open.' : 'Your plan has ended and your store is closed. Renew to reopen it.'}
        </p>
      )}
      <div>
        <Link to="/merchant/onboarding"><button type="button">{sub?.status === 'NONE' ? 'Choose a plan' : 'Renew or change plan'}</button></Link>
      </div>

      {sum && (
        <>
          <div className="stats">
            <div className="stat"><span className="muted">Paid orders</span><strong>{sum.orders}</strong></div>
            <div className="stat"><span className="muted">Gross sales</span><strong>{money(sum.gross)}</strong></div>
            <div className="stat"><span className="muted">Platform commission</span><strong>{money(sum.commission)}</strong></div>
            <div className="stat"><span className="muted">Your earnings</span><strong>{money(sum.net)}</strong></div>
          </div>
          {Number(sum.codCommissionDue) > 0 && (
            <p className="notice">
              You collected cash on delivery, so {money(sum.codCommissionDue)} of commission is due to the platform owner.
            </p>
          )}
        </>
      )}
    </section>
  )
}
