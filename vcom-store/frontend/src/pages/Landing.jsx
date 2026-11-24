import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api'
import PlanCards from '../PlanCards.jsx'
import Stores from './Stores.jsx'

export default function Landing() {
  const [plans, setPlans] = useState([])
  useEffect(() => { api.get('/payments/plans').then((r) => setPlans(r.data)).catch(() => {}) }, [])

  return (
    <>
      <section className="hero">
        <h1>Launch your own online store</h1>
        <p>Every merchant gets a storefront of their own, with their own products, customers and orders.</p>
        <Link to="/merchant/register"><button>Start selling</button></Link>
      </section>
      {plans.length > 0 && (
        <section aria-labelledby="pricing">
          <h2 id="pricing">Simple pricing</h2>
          <PlanCards plans={plans} />
          <p className="muted">Pick your plan right after you create your merchant account.</p>
        </section>
      )}
      <Stores />
    </>
  )
}
