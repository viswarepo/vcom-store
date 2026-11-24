import { money } from './money.js'

// Search, category, price range and sorting. Shared by the storefront and the merchant's product list.
export const SORTS = [
  ['latest', 'Latest first'],
  ['price_asc', 'Price: low to high'],
  ['price_desc', 'Price: high to low'],
]

/** A message when the price range can't be used, otherwise ''. Callers skip the request while this is set. */
export const priceError = (min, max) =>
  min !== '' && max !== '' && Number(min) > Number(max) ? 'The minimum price is higher than the maximum.' : ''

export default function ProductFilters({
  q, setQ, category, setCategory, sort, setSort, minPrice, setMinPrice, maxPrice, setMaxPrice,
  categories, priceRange, placeholder = 'Search products',
}) {
  // keep the chosen category selectable even if it is no longer in the list
  const options = category && !categories.some((c) => c.toLowerCase() === category.toLowerCase()) ? [...categories, category] : categories
  const active = q || category || minPrice !== '' || maxPrice !== '' || sort !== 'latest'
  const error = priceError(minPrice, maxPrice)
  const hint = (v) => (v == null ? '' : money(v))

  return (
    <section className="toolbar" aria-label="Search and filter products" style={{ flexWrap: 'wrap' }}>
      <input type="search" placeholder={placeholder} value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search products by name" />
      <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="Filter by category">
        <option value="">All categories</option>
        {options.map((c) => <option key={c} value={c}>{c}</option>)}
      </select>
      <input type="number" inputMode="decimal" min="0" step="any" style={{ width: '9rem', flex: '0 0 auto' }}
        placeholder={priceRange?.min != null ? `Min ${hint(priceRange.min)}` : 'Min price'}
        value={minPrice} onChange={(e) => setMinPrice(e.target.value)} aria-label="Minimum price in rupees" aria-invalid={!!error} />
      <input type="number" inputMode="decimal" min="0" step="any" style={{ width: '9rem', flex: '0 0 auto' }}
        placeholder={priceRange?.max != null ? `Max ${hint(priceRange.max)}` : 'Max price'}
        value={maxPrice} onChange={(e) => setMaxPrice(e.target.value)} aria-label="Maximum price in rupees" aria-invalid={!!error} />
      <select value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort products">
        {SORTS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
      {active && (
        <button type="button" className="ghost" onClick={() => { setQ(''); setCategory(''); setMinPrice(''); setMaxPrice(''); setSort('latest') }}>Clear</button>
      )}
      {error && <p className="error" role="alert" style={{ flexBasis: '100%', margin: 0 }}>{error}</p>}
    </section>
  )
}
