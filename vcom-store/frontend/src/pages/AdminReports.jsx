import { Link } from 'react-router-dom'
import ReportView from '../ReportView.jsx'

export default function AdminReports() {
  return (
    <>
      <p className="no-print"><Link to="/admin" className="muted">← Platform owner</Link></p>
      <ReportView admin />
    </>
  )
}
