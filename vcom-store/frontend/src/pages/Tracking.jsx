// Delivery tracking for one order. Used by the customer's order page and the merchant's order list.
const STEPS = [
  ['PLACED', 'Order placed'],
  ['CONFIRMED', 'Confirmed'],
  ['PACKED', 'Packed'],
  ['SHIPPED', 'Shipped'],
  ['DELIVERED', 'Delivered'],
]

const safeUrl = (u) => /^https?:\/\/\S+$/i.test(u || '')
const fmt = (d) => new Date(d).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })

export default function Tracking({ order }) {
  const history = order.history || []
  const at = (status) => history.find((h) => h.status === status)?.at
  const cancelled = order.status === 'CANCELLED'
  const lastDone = STEPS.reduce((n, [s], i) => (at(s) ? i : n), -1)
  const activity = [...history].reverse()

  return (
    <section className="tracking" aria-label="Delivery tracking">
      {!cancelled && (
        <ol className="track">
          {STEPS.map(([status, label], i) => (
            <li key={status} className={i <= lastDone ? 'done' : ''} aria-current={i === lastDone ? 'step' : undefined}>
              <span className="dot" aria-hidden="true" />
              <strong>{label}</strong>
              <span className="muted">{at(status) ? fmt(at(status)) : ''}</span>
            </li>
          ))}
        </ol>
      )}

      {(order.courier || order.trackingNumber || order.estimatedDelivery) && (
        <dl className="ship-info">
          {order.courier && <><dt>Courier</dt><dd>{order.courier}</dd></>}
          {order.trackingNumber && (
            <>
              <dt>Tracking number</dt>
              <dd>
                {safeUrl(order.trackingUrl)
                  ? <a href={order.trackingUrl} target="_blank" rel="noopener noreferrer">{order.trackingNumber}</a>
                  : order.trackingNumber}
              </dd>
            </>
          )}
          {order.estimatedDelivery && order.fulfillment !== 'DELIVERED' && (
            <><dt>Expected by</dt><dd>{new Date(order.estimatedDelivery + 'T00:00:00').toLocaleDateString([], { dateStyle: 'medium' })}</dd></>
          )}
        </dl>
      )}

      {activity.length > 0 && (
        <>
          <h3>Activity</h3>
          <ul className="activity">
            {activity.map((h, i) => (
              <li key={i}><span className="muted">{fmt(h.at)}</span> {h.message}</li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
