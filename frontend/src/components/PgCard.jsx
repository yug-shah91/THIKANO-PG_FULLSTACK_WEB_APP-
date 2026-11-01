import { Link } from 'react-router-dom'
import PgVisual from './PgVisual'
import Beds from './Beds'
import { AmenityIcons } from './Amenities'
import { Rating } from './Stars'
import { PinIcon } from './Icons'
import { money } from './UI'

const GENDER = { MEN_ONLY: 'Boys PG', WOMEN_ONLY: 'Girls PG' }
const FOOD = /meal|food|mess|tiffin|breakfast|dinner/i
const typeLabel = (t) => t[0] + t.slice(1).toLowerCase()

// One PG in search results. Rent first, then where it is, then what is free.
export default function PgCard({ pg, selected = false, onHover }) {
  const free = pg.freeBeds
  return (
    <Link className={`pg-card ${selected ? 'sel' : ''}`} to={`/pgs/${pg.id}`}
      onMouseEnter={onHover ? () => onHover(pg) : undefined}>
      <PgVisual pg={pg}>
        <span className={`pill ${free === 0 ? 'full' : free <= 2 ? 'low' : ''}`}>
          {free === 0 ? 'Full right now' : `${free} ${free === 1 ? 'bed' : 'beds'} free`}
        </span>
        {GENDER[pg.genderPolicy] && <span className={`gtag ${pg.genderPolicy === 'MEN_ONLY' ? 'men' : 'women'}`}>{GENDER[pg.genderPolicy]}</span>}
      </PgVisual>
      <div className="pg-body">
        <div className="pg-price">{money(pg.startingRent)} <small>per month, from</small></div>
        <div className="name-row">
          <h3>{pg.name}</h3>
          <Rating avg={pg.ratingAvg} count={pg.reviewCount} />
        </div>
        <div className="pg-loc">
          <PinIcon width={15} height={15} />
          <span>{pg.city}{pg.distanceKm != null && <>, <b>{pg.distanceKm} km away</b></>}</span>
        </div>
        <div className="pg-meta">
          <span>{pg.roomTypes.length > 0 ? pg.roomTypes.map(typeLabel).join(', ') + ' sharing' : 'No rooms open'}</span>
          {(pg.amenities || []).some((a) => FOOD.test(a)) && <span className="food">Meals included</span>}
        </div>
        <div className="pg-foot">
          <AmenityIcons items={pg.amenities || []} max={4} />
          <Beds free={free} max={5} />
        </div>
      </div>
    </Link>
  )
}
