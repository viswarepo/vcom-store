import { limitLabel, priceLabel } from './plans.js'

// Read-only when onSelect is missing (pricing section), selectable during onboarding.
export default function PlanCards({ plans, selectedId, onSelect }) {
  return (
    <div className="plans" role={onSelect ? 'radiogroup' : undefined} aria-label="Plans">
      {plans.map((p) => {
        const on = p.id === selectedId
        return (
          <article key={p.id} className={`plan ${on ? 'on' : ''}`}>
            <h3>{p.name}</h3>
            <p className="plan-price">{priceLabel(p)}</p>
            {p.description && <p className="muted">{p.description}</p>}
            <ul>
              <li>{Number(p.commissionPercent)}% commission on each sale</li>
              <li>{limitLabel(p.maxProducts)}</li>
              <li>Your own storefront and customers</li>
            </ul>
            {onSelect && (
              <button type="button" role="radio" aria-checked={on} className={on ? '' : 'ghost'} onClick={() => onSelect(p.id)}>
                {on ? 'Selected' : `Choose ${p.name}`}
              </button>
            )}
          </article>
        )
      })}
    </div>
  )
}
