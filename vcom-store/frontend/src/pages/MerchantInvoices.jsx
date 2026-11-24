import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api'
import { money } from '../money.js'

export default function MerchantInvoices() {
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/orders/merchant/invoices').then((r) => setRows(r.data)).catch((e) => setError(errorMessage(e)))
  }, [])

  if (!rows) return error ? <p className="empty error">{error}</p> : <p className="empty">Loading…</p>

  return (
    <div className="dash">
      <p><Link to="/dashboard" className="muted">← Dashboard</Link></p>
      <h1>Invoices ({rows.length})</h1>
      <p className="muted">An invoice is issued automatically when an order is paid. Open one to print it or save it as a PDF.</p>
      {rows.length === 0 && <p className="empty">No invoices yet.</p>}
      <table className="table">
        <tbody>
          {rows.map((r) => (
            <tr key={r.orderId}>
              <td><strong>{r.number}</strong></td>
              <td className="muted">{new Date(r.issuedAt).toLocaleDateString()}</td>
              <td>{r.customerName}</td>
              <td className="price">{money(r.total)}</td>
              <td className="actions"><Link to={`/dashboard/orders/${r.orderId}/invoice`}><button className="ghost" type="button">View</button></Link></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
