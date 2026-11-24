import { money } from './money.js'

export const periodLabel = { MONTHLY: 'month', YEARLY: 'year' }
export const priceLabel = (p) => (Number(p.price) === 0 ? 'Free' : `${money(p.price)} / ${periodLabel[p.billingPeriod]}`)
export const limitLabel = (n) => (n == null ? 'Unlimited products' : `Up to ${n} products`)
