import { Link } from 'react-router-dom'
import ReportView from '../ReportView.jsx'

export default function MerchantReports() {
  return (
    <>
      <p className="no-print"><Link to="/dashboard" className="muted">← Dashboard</Link></p>
      <ReportView />
    </>
  )
}
