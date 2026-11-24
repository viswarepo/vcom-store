import { useEffect, useMemo, useState } from 'react'
import api, { errorMessage } from './api'
import { money } from './money.js'

const iso = (d) => (d ? `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` : '')
const GRANULARITIES = [['DAY', 'Daily'], ['MONTH', 'Monthly'], ['QUARTER', 'Quarterly'], ['YEAR', 'Annual']]

// Preset date ranges. Quarters and years follow the financial year the server reports (April start in India).
function presetRange(key, fyStartMonth) {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth()
  const fy = fyStartMonth - 1
  const fyYear = m >= fy ? y : y - 1
  const off = (((m - fy) % 12) + 12) % 12
  const qStart = new Date(fyYear, fy + Math.floor(off / 3) * 3, 1)
  const qEnd = (s) => new Date(s.getFullYear(), s.getMonth() + 3, 0)
  switch (key) {
    case 'thisMonth': return [new Date(y, m, 1), new Date(y, m + 1, 0), 'DAY']
    case 'lastMonth': return [new Date(y, m - 1, 1), new Date(y, m, 0), 'DAY']
    case 'thisQuarter': return [qStart, qEnd(qStart), 'MONTH']
    case 'lastQuarter': { const s = new Date(qStart.getFullYear(), qStart.getMonth() - 3, 1); return [s, qEnd(s), 'MONTH'] }
    case 'thisYear': return [new Date(fyYear, fy, 1), new Date(fyYear + 1, fy, 0), 'MONTH']
    case 'lastYear': return [new Date(fyYear - 1, fy, 1), new Date(fyYear, fy, 0), 'QUARTER']
    default: return [null, null, 'YEAR']   // all time
  }
}

const csvCell = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`

function Bars({ periods, series }) {
  const max = Math.max(1, ...periods.flatMap((p) => series.map((s) => Number(s.value(p)))))
  return (
    <div className="bars" role="img" aria-label={`Bar chart of ${series.map((s) => s.label).join(' and ')} for each period. The same figures are in the table below.`}>
      {periods.map((p) => (
        <div key={p.start} className="bar-group" title={`${p.label}: ${series.map((s) => `${s.label} ${money(s.value(p))}`).join(', ')}`}>
          <div className="bar-stack">
            {series.map((s) => (
              <span key={s.label} className={`bar ${s.cls}`} style={{ height: `${Math.max(1, (Number(s.value(p)) / max) * 100)}%` }} />
            ))}
          </div>
          <span className="bar-label">{p.label}</span>
        </div>
      ))}
    </div>
  )
}

/**
 * Sales report for the store owner (admin=false) or for the platform owner (admin=true).
 * Choose a preset or a custom date range, and the interval: daily, monthly, quarterly or annual.
 */
export default function ReportView({ admin = false }) {
  const [cfg, setCfg] = useState(null)
  const [preset, setPreset] = useState('thisYear')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [granularity, setGranularity] = useState('MONTH')
  const [data, setData] = useState(null)
  const [names, setNames] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const endpoint = admin ? '/payments/reports/admin' : '/payments/reports/merchant'

  useEffect(() => {
    api.get('/payments/reports/config').then((r) => setCfg(r.data)).catch((e) => setError(errorMessage(e)))
    if (admin) api.get('/stores').then((r) => setNames(Object.fromEntries(r.data.map((s) => [s.ownerId, s.name])))).catch(() => {})
  }, [admin])

  // apply a preset once the financial-year setting is known
  useEffect(() => {
    if (!cfg || preset === 'custom') return
    const [a, b, g] = presetRange(preset, cfg.fiscalYearStartMonth)
    setFrom(iso(a)); setTo(iso(b)); setGranularity(g)
  }, [cfg, preset])

  useEffect(() => {
    if (!cfg) return
    setLoading(true); setError('')
    api.get(endpoint, { params: { from: from || undefined, to: to || undefined, granularity } })
      .then((r) => setData(r.data))
      .catch((e) => { setError(errorMessage(e)); setData(null) })
      .finally(() => setLoading(false))
  }, [cfg, endpoint, from, to, granularity])

  const yearLabel = cfg && cfg.fiscalYearStartMonth !== 1 ? 'financial year' : 'year'
  const presets = [
    ['thisMonth', 'This month'], ['lastMonth', 'Last month'], ['thisQuarter', 'This quarter'], ['lastQuarter', 'Last quarter'],
    ['thisYear', `This ${yearLabel}`], ['lastYear', `Last ${yearLabel}`], ['all', 'All time'], ['custom', 'Custom'],
  ]

  const rows = useMemo(() => (data?.periods || []).map((p) => ({ ...p, ownerRevenue: Number(p.commission) + Number(p.subscriptions) })), [data])

  const series = admin
    ? [{ label: 'Gross sales', cls: 'a', value: (p) => p.gross }, { label: 'Your revenue', cls: 'b', value: (p) => Number(p.commission) + Number(p.subscriptions) }]
    : [{ label: 'Gross sales', cls: 'a', value: (p) => p.gross }, { label: 'Your earnings', cls: 'b', value: (p) => p.net }]

  const downloadCsv = () => {
    const head = admin
      ? ['Period', 'Start', 'End', 'Orders', 'Gross sales', 'Commission', 'Subscriptions', 'Your revenue']
      : ['Period', 'Start', 'End', 'Orders', 'Gross sales', 'Platform commission', 'Your earnings']
    const lines = rows.map((p) => (admin
      ? [p.label, p.start, p.end, p.orders, p.gross, p.commission, p.subscriptions, p.ownerRevenue.toFixed(2)]
      : [p.label, p.start, p.end, p.orders, p.gross, p.commission, p.net]))
    const t = data.totals
    lines.push(admin
      ? ['Total', from, to, t.orders, t.gross, t.commission, t.subscriptions, (Number(t.commission) + Number(t.subscriptions)).toFixed(2)]
      : ['Total', from, to, t.orders, t.gross, t.commission, t.net])
    const csv = '\uFEFF' + [head, ...lines].map((r) => r.map(csvCell).join(',')).join('\n')
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    a.download = `${admin ? 'platform' : 'store'}-sales-${from || 'start'}-to-${to || 'today'}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
  }

  const t = data?.totals
  const periodText = data ? `${from ? new Date(from + 'T00:00:00').toLocaleDateString() : 'The beginning'} to ${to ? new Date(to + 'T00:00:00').toLocaleDateString() : 'today'}` : ''

  return (
    <div className="dash report">
      <h1>{admin ? 'Platform sales report' : 'Sales report'}</h1>

      <div className="no-print report-controls">
        <div className="chips" role="group" aria-label="Period">
          {presets.map(([key, label]) => (
            <button key={key} type="button" aria-pressed={preset === key} className={`chip ${preset === key ? 'on' : ''}`} onClick={() => setPreset(key)}>{label}</button>
          ))}
        </div>
        <div className="two">
          <label>From<input type="date" value={from} max={to || undefined} onChange={(e) => { setPreset('custom'); setFrom(e.target.value) }} /></label>
          <label>To<input type="date" value={to} min={from || undefined} onChange={(e) => { setPreset('custom'); setTo(e.target.value) }} /></label>
        </div>
        <div className="row" style={{ justifyContent: 'flex-start' }}>
          <label className="inline">Break down by
            <select value={granularity} onChange={(e) => setGranularity(e.target.value)}>
              {GRANULARITIES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
            </select>
          </label>
          <button type="button" className="ghost" onClick={downloadCsv} disabled={!data}>Download CSV</button>
          <button type="button" className="ghost" onClick={() => window.print()} disabled={!data}>Print</button>
        </div>
      </div>

      {error && <p className="error" role="alert">{error}</p>}
      {loading && !data && <p className="empty">Loading…</p>}

      {t && (
        <>
          <p className="muted" role="status">{periodText}{cfg ? ` · ${cfg.zone}` : ''}{loading ? ' · updating…' : ''}</p>
          <div className="stats">
            <div className="stat"><span className="muted">Paid orders</span><strong>{t.orders}</strong></div>
            <div className="stat"><span className="muted">Gross sales</span><strong>{money(t.gross)}</strong></div>
            {admin ? (
              <>
                <div className="stat"><span className="muted">Commission</span><strong>{money(t.commission)}</strong></div>
                <div className="stat"><span className="muted">Subscriptions</span><strong>{money(t.subscriptions)}</strong></div>
                <div className="stat"><span className="muted">Your revenue</span><strong>{money(Number(t.commission) + Number(t.subscriptions))}</strong></div>
              </>
            ) : (
              <>
                <div className="stat"><span className="muted">Platform commission</span><strong>{money(t.commission)}</strong></div>
                <div className="stat"><span className="muted">Your earnings</span><strong>{money(t.net)}</strong></div>
              </>
            )}
            <div className="stat"><span className="muted">Average order</span><strong>{money(t.averageOrder)}</strong></div>
          </div>
          {Number(t.codCommissionDue) > 0 && (
            <p className="notice">{money(t.codCommissionDue)} of commission on cash-on-delivery orders in this period {admin ? 'is owed by merchants' : 'is due to the platform owner'}.</p>
          )}

          {rows.length > 0 && (
            <>
              <h2>{GRANULARITIES.find(([k]) => k === granularity)[1]} breakdown</h2>
              <div className="legend">
                {series.map((s) => <span key={s.label}><span className={`swatch ${s.cls}`} aria-hidden="true" /> {s.label}</span>)}
              </div>
              <Bars periods={rows} series={series} />
              <div className="table-scroll">
                <table className="table">
                  <thead>
                    <tr className="muted">
                      <td>Period</td><td>Orders</td><td>Gross sales</td><td>{admin ? 'Commission' : 'Platform commission'}</td>
                      {admin ? <><td>Subscriptions</td><td>Your revenue</td></> : <td>Your earnings</td>}
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((p) => (
                      <tr key={p.start}>
                        <td>{p.label}</td><td>{p.orders}</td><td>{money(p.gross)}</td><td>{money(p.commission)}</td>
                        {admin ? <><td>{money(p.subscriptions)}</td><td>{money(p.ownerRevenue)}</td></> : <td>{money(p.net)}</td>}
                      </tr>
                    ))}
                    <tr className="total-row">
                      <td>Total</td><td>{t.orders}</td><td>{money(t.gross)}</td><td>{money(t.commission)}</td>
                      {admin ? <><td>{money(t.subscriptions)}</td><td>{money(Number(t.commission) + Number(t.subscriptions))}</td></> : <td>{money(t.net)}</td>}
                    </tr>
                  </tbody>
                </table>
              </div>
            </>
          )}
          {rows.length === 0 && <p className="empty">No sales in this period.</p>}

          {data.byMethod.length > 0 && (
            <>
              <h2>By payment method</h2>
              <table className="table">
                <tbody>
                  {data.byMethod.map((m) => <tr key={m.method}><td>{m.method}</td><td>{m.orders} orders</td><td>{money(m.gross)}</td></tr>)}
                </tbody>
              </table>
            </>
          )}

          {admin && data.topMerchants.length > 0 && (
            <>
              <h2>Top merchants in this period</h2>
              <table className="table">
                <thead><tr className="muted"><td>Merchant</td><td>Orders</td><td>Gross sales</td><td>Commission</td></tr></thead>
                <tbody>
                  {data.topMerchants.map((m) => (
                    <tr key={m.merchantId}>
                      <td>{names[m.merchantId] || `Merchant ${m.merchantId.slice(0, 8)}`}</td>
                      <td>{m.orders}</td><td>{money(m.gross)}</td><td>{money(m.commission)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
          <p className="muted">Sales are counted on the day they were paid. Cash-on-delivery orders count when the order is confirmed. There are no refunds in these figures yet.</p>
        </>
      )}
    </div>
  )
}
