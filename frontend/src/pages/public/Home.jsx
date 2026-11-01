import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { publicApi } from '../../api/services'
import PgCard from '../../components/PgCard'
import { SkeletonGrid } from '../../components/UI'

// Landing page. Every number on it is counted from the live listings, nothing is made up.
export default function Home() {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [maxRent, setMaxRent] = useState('')
  const [pgs, setPgs] = useState(null)

  useEffect(() => {
    publicApi.search({ onlyAvailable: false }).then((r) => setPgs(r.data)).catch(() => setPgs([]))
  }, [])

  const go = (e) => {
    e.preventDefault()
    const p = new URLSearchParams()
    if (q.trim()) p.set('q', q.trim())
    if (maxRent) p.set('maxRent', maxRent)
    navigate(`/pgs${p.toString() ? `?${p}` : ''}`)
  }

  const list = pgs || []
  const freeBeds = list.reduce((n, p) => n + p.freeBeds, 0)
  const reviews = list.reduce((n, p) => n + (p.reviewCount || 0), 0)
  const withPhotos = list.filter((p) => p.coverPhotoId && p.freeBeds > 0)
    .sort((a, b) => (b.ratingAvg || 0) - (a.ratingAvg || 0)).slice(0, 3)
  // cities that really have a listing, most listings first
  const cityCount = {}
  list.forEach((p) => { const c = (p.city || '').split(',').pop().trim(); if (c) cityCount[c] = (cityCount[c] || 0) + 1 })
  const cities = Object.keys(cityCount).sort((a, b) => cityCount[b] - cityCount[a]).slice(0, 5)
  const featured = [...list].filter((p) => p.freeBeds > 0)
    .sort((a, b) => (b.ratingAvg || 0) - (a.ratingAvg || 0) || b.freeBeds - a.freeBeds).slice(0, 3)

  return (
    <div>
      <div>
        <section className="ed-hero">
          <h1>Find a place that feels like home.</h1>
          <p className="sub">Discover PGs, compare rooms and find a place that fits your budget.</p>
          <form className="find" onSubmit={go} role="search">
            <label>
              <span>Location</span>
              <input placeholder="City, area or PG name" value={q} onChange={(e) => setQ(e.target.value)} />
            </label>
            <label>
              <span>Budget per month</span>
              <input type="number" min="0" step="500" placeholder="Any" value={maxRent} onChange={(e) => setMaxRent(e.target.value)} />
            </label>
            <button className="btn btn-lg">Explore PGs</button>
          </form>
          <div className="quick">
            <Link className="chip" to="/pgs?gender=MEN_ONLY">Boys PG</Link>
            <Link className="chip" to="/pgs?gender=WOMEN_ONLY">Girls PG</Link>
            {cities.map((c) => <Link key={c} className="chip" to={`/pgs?q=${encodeURIComponent(c)}`}>{c}</Link>)}
          </div>
          <div className="find-sec">
            <span className="muted">Own a PG?</span>
            <Link className="link" to="/register?role=OWNER">List your property</Link>
          </div>
        </section>

        {pgs && pgs.length > 0 && (
          <dl className="facts-row">
            <div><dt>PGs listed</dt><dd>{list.length}</dd></div>
            <div><dt>Beds free today</dt><dd>{freeBeds}</dd></div>
            {reviews > 0 && <div><dt>Tenant reviews</dt><dd>{reviews}</dd></div>}
          </dl>
        )}

        {withPhotos.length > 0 && (
          <section className="ed-feature" aria-label="Featured stay">
            <Link to={`/pgs/${withPhotos[0].id}`} className="bleed-img">
              <img src={`/api/public/pg-photos/${withPhotos[0].coverPhotoId}`} alt={withPhotos[0].name} />
            </Link>
            <div className="ed-cap">
              <span className="muted small">Featured today</span>
              <h2>{withPhotos[0].name}, {withPhotos[0].city}</h2>
              <Link className="btn btn-ghost btn-lg" to={`/pgs/${withPhotos[0].id}`}>See the PG</Link>
            </div>
          </section>
        )}

        <section className="band">
          <div className="band-head">
            <h2>Beds available now</h2>
            <Link to="/pgs">See all PGs</Link>
          </div>
          {!pgs ? <SkeletonGrid n={3} /> : featured.length === 0 ? (
            <div className="empty-state"><h3>No beds are free right now</h3><p>Check back soon, or browse every PG to see when rooms open up.</p><Link className="btn btn-ghost" to="/pgs">Browse all PGs</Link></div>
          ) : (
            <div className="pg-grid">{featured.map((p) => <PgCard key={p.id} pg={p} />)}</div>
          )}
        </section>

        <section className="band" style={{ paddingTop: 8 }}>
          <div className="band-head"><h2>How it works</h2></div>
          <ol className="steps">
            <li><h3>Search</h3><p>Filter by budget, room type, gender preference and facilities, or look at the map.</p></li>
            <li><h3>Compare</h3><p>See the rent, extra charges, free beds and tenant reviews side by side before you call.</p></li>
            <li><h3>Book or visit</h3><p>Book a free bed, or schedule a visit first. The owner confirms it.</p></li>
          </ol>
        </section>

        <section className="band" style={{ paddingTop: 8 }}>
          <div className="owner-band">
            <div>
              <h2>Run a PG? List it on Thikano.</h2>
              <p>Add rooms and charges, take bookings, collect rent and answer complaints from one dashboard.</p>
            </div>
            <Link className="btn btn-sand btn-lg" to="/register?role=OWNER">List your property</Link>
          </div>
        </section>
      </div>
    </div>
  )
}
