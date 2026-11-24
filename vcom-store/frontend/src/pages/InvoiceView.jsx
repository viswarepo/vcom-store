import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { useStoreCtx } from '../store.jsx'
import { money } from '../money.js'

// Used by the merchant (/dashboard/orders/:id/invoice) and by the buyer (/store/:slug/orders/:id/invoice)
export default function InvoiceView() {
  const { id } = useParams()
  const ctx = useStoreCtx()
  const back = ctx ? `${ctx.base}/orders/${id}` : '/dashboard/invoices'
  const [inv, setInv] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get(`/orders/${id}/invoice`).then((r) => setInv(r.data)).catch((e) => setError(errorMessage(e)))
  }, [id])

  if (error) return <p className="empty error">{error} <Link to={back}>Back</Link></p>
  if (!inv) return <p className="empty">Loading…</p>

  const Party = ({ title, p }) => (
    <div>
      <p className="muted" style={{ margin: 0 }}>{title}</p>
      <strong>{p.name}</strong>
      {p.address && <div style={{ whiteSpace: 'pre-line' }}>{p.address}</div>}
      {p.email && <div>{p.email}</div>}
      {p.gstin && <div>GSTIN: {p.gstin}</div>}
    </div>
  )

  return (
    <div className="invoice">
      <div className="no-print row">
        <Link to={back} className="muted">← Back</Link>
        <button onClick={() => window.print()}>Print or save as PDF</button>
      </div>
      <div className="invoice-head">
        <h1>Invoice</h1>
        <div className="invoice-meta">
          <strong>{inv.number}</strong>
          <span>{new Date(inv.issuedAt).toLocaleDateString()}</span>
          <span className="muted">Order #{inv.orderId.slice(0, 8)}</span>
        </div>
      </div>
      <div className="two">
        <Party title="From" p={inv.seller} />
        <Party title="Bill to" p={inv.buyer} />
      </div>
      <table className="table">
        <thead><tr className="muted"><td>Item</td><td>Qty</td><td>Price</td><td>Amount</td></tr></thead>
        <tbody>
          {inv.lines.map((l) => (
            <tr key={l.productId}>
              <td>{l.name}</td>
              <td>{l.quantity}</td>
              <td>{money(l.price)}</td>
              <td>{money(l.price * l.quantity)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="cart-total"><span className="muted">{inv.paymentNote}</span><strong>Total {money(inv.total)}</strong></div>
      <p className="muted">Amounts are as charged at checkout. Tax is not itemised on this invoice.</p>
    </div>
  )
}
