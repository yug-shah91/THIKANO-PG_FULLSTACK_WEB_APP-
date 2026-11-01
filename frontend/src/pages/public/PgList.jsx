import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { publicApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import PgCard from '../../components/PgCard'
import PgMap from '../../components/PgMap'
import { FILTER_AMENITIES } from '../../components/Amenities'
import { ListIcon, MapIcon, PinIcon, SlidersIcon } from '../../components/Icons'
import { SkeletonGrid } from '../../components/UI'

const TYPES = ['SINGLE', 'DOUBLE', 'TRIPLE', 'DORMITORY']
const GENDERS = [['', 'Any'], ['MEN_ONLY', 'Boys'], ['WOMEN_ONLY', 'Girls']]
const label = (t) => t[0] + t.slice(1).toLowerCase()

// Search page. Every filter lives in the URL, so a search can be shared and the back button works.
export default function PgList() {
  const toast = useToast()
  const [sp, setSp] = useSearchParams()
  const get = (k, d = '') => sp.get(k) ?? d
  const amenities = sp.getAll('amenities')
  const view = get('view', 'list')
  const onlyAvailable = get('free', '1') === '1'
  const radiusKm = Number(get('km', '10'))

  // text boxes keep their own draft and update the URL after a short pause
  const [q, setQ] = useState(get('q'))
  const [minRent, setMinRent] = useState(get('minRent'))
  const [maxRent, setMaxRent] = useState(get('maxRent'))
  const [coords, setCoords] = useState(null)
  const [locating, setLocating] = useState(false)
  const [pgs, setPgs] = useState(null)
  const [error, setError] = useState('')
  const [picked, setPicked] = useState(null)
  const [panelOpen, setPanelOpen] = useState(false)
  const pick = useCallback((p) => setPicked(p), [])

  const patch = useCallback((changes) => {
    setSp((old) => {
      const n = new URLSearchParams(old)
      Object.entries(changes).forEach(([k, v]) => {
        n.delete(k)
        if (Array.isArray(v)) v.forEach((x) => n.append(k, x))
        else if (v !== '' && v != null) n.set(k, v)
      })
      return n
    }, { replace: true })
  }, [setSp])

  const first = useRef(true)
  useEffect(() => {
    if (first.current) { first.current = false; return }
    const t = setTimeout(() => patch({ q: q.trim(), minRent, maxRent }), 400)
    return () => clearTimeout(t)
  }, [q, minRent, maxRent, patch])

  const key = sp.toString() + (coords ? `|${coords.lat},${coords.lng}` : '')
  useEffect(() => {
    let current = true // ignore an older answer that arrives after a newer search
    setPgs(null); setError('')
    publicApi.search({
      q: get('q') || undefined,
      maxRent: get('maxRent') || undefined,
      roomType: get('type') || undefined,
      gender: get('gender') || undefined,
      amenities: amenities.length ? amenities : undefined,
      onlyAvailable,
      lat: coords?.lat, lng: coords?.lng, radiusKm: coords ? radiusKm : undefined,
    }).then((r) => { if (current) setPgs(r.data) })
      .catch((e) => { if (current) { setError(errMsg(e)); setPgs([]) } })
    return () => { current = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  // minimum rent is not a server filter, so it is applied here
  const shown = useMemo(() => {
    if (!pgs) return null
    const lo = Number(get('minRent') || 0)
    return lo > 0 ? pgs.filter((p) => Number(p.startingRent || 0) >= lo) : pgs
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pgs, sp])

  const toggleAmenity = (v) => patch({ amenities: amenities.includes(v) ? amenities.filter((a) => a !== v) : [...amenities, v] })

  const useMyLocation = () => {
    if (coords) { setCoords(null); return }
    if (!navigator.geolocation) { toast('Your browser does not support location. Search by city instead.', 'error'); return }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (pos) => { setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude }); setLocating(false) },
      (err) => {
        setLocating(false)
        toast(err.code === 1 ? 'Location is blocked. Allow it in your browser settings, or search by city.' : 'Could not find your location. Search by city instead.', 'error')
      },
      { timeout: 10000 })
  }

  const clearAll = () => { setQ(''); setMinRent(''); setMaxRent(''); setCoords(null); setSp({}, { replace: true }) }

  const tags = []
  if (get('q')) tags.push([`“${get('q')}”`, () => { setQ(''); patch({ q: '' }) }])
  if (get('minRent') || get('maxRent')) tags.push([`₹${get('minRent') || '0'} to ${get('maxRent') ? '₹' + get('maxRent') : 'any'}`, () => { setMinRent(''); setMaxRent(''); patch({ minRent: '', maxRent: '' }) }])
  if (get('type')) tags.push([`${label(get('type'))} room`, () => patch({ type: '' })])
  if (get('gender')) tags.push([GENDERS.find((g) => g[0] === get('gender'))?.[1] + ' PG', () => patch({ gender: '' })])
  amenities.forEach((a) => tags.push([FILTER_AMENITIES.find((f) => f.value === a)?.label || a, () => toggleAmenity(a)]))
  if (coords) tags.push([`Within ${radiusKm} km`, () => setCoords(null)])

  return (
    <>
      <div className="search-head">
        <div>
          <h1>Find a PG</h1>
          <p className="muted" style={{ margin: '4px 0 0' }}>Rent, free beds and facilities for every listing.</p>
        </div>
        <button className="btn btn-ghost filter-toggle" aria-expanded={panelOpen} onClick={() => setPanelOpen((o) => !o)}>
          <SlidersIcon width={16} height={16} />&nbsp;Filters{tags.length ? ` (${tags.length})` : ''}
        </button>
      </div>

      <div className="search-layout">
        <aside className={`filter-panel ${panelOpen ? 'open' : ''}`} aria-label="Filters">
          <fieldset>
            <legend>Location</legend>
            <input type="search" aria-label="City, area or PG name" placeholder="City, area or PG name" value={q} onChange={(e) => setQ(e.target.value)} />
            <button type="button" className={`chip ${coords ? 'on' : ''}`} style={{ marginTop: 10 }} onClick={useMyLocation} disabled={locating}>
              <PinIcon width={15} height={15} /> {locating ? 'Finding you…' : coords ? 'Near me: on' : 'Near me'}
            </button>
            {coords && (
              <label className="f-opt" style={{ marginTop: 6 }}>
                Within
                <select value={radiusKm} onChange={(e) => patch({ km: e.target.value })}>
                  {[2, 5, 10, 25, 50].map((k) => <option key={k} value={k}>{k} km</option>)}
                </select>
              </label>
            )}
          </fieldset>

          <fieldset>
            <legend>Budget per month</legend>
            <div className="f-row">
              <input type="number" min="0" step="500" placeholder="Min" aria-label="Minimum rent" value={minRent} onChange={(e) => setMinRent(e.target.value)} />
              <input type="number" min="0" step="500" placeholder="Max" aria-label="Maximum rent" value={maxRent} onChange={(e) => setMaxRent(e.target.value)} />
            </div>
          </fieldset>

          <fieldset>
            <legend>Boys or girls PG</legend>
            <div className="f-seg three">
              {GENDERS.map(([v, l]) => (
                <button key={v} type="button" className={get('gender') === v ? 'on' : ''} aria-pressed={get('gender') === v} onClick={() => patch({ gender: v })}>{l}</button>
              ))}
            </div>
          </fieldset>

          <fieldset>
            <legend>Sharing type</legend>
            <div className="f-seg">
              <button type="button" className={!get('type') ? 'on' : ''} aria-pressed={!get('type')} onClick={() => patch({ type: '' })}>Any</button>
              {TYPES.map((t) => (
                <button key={t} type="button" className={get('type') === t ? 'on' : ''} aria-pressed={get('type') === t} onClick={() => patch({ type: t })}>{label(t)}</button>
              ))}
            </div>
          </fieldset>

          <fieldset>
            <legend>Facilities</legend>
            {FILTER_AMENITIES.map(({ value, label: l, Icon }) => (
              <label className="f-opt" key={value}>
                <input type="checkbox" checked={amenities.includes(value)} onChange={() => toggleAmenity(value)} />
                <Icon width={16} height={16} /> {l}
              </label>
            ))}
          </fieldset>

          <fieldset>
            <legend>Availability</legend>
            <label className="f-opt">
              <input type="checkbox" checked={onlyAvailable} onChange={() => patch({ free: onlyAvailable ? '0' : '' })} />
              Only PGs with free beds
            </label>
          </fieldset>
          {tags.length > 0 && <button type="button" className="btn btn-ghost btn-sm" onClick={clearAll}>Clear all filters</button>}
        </aside>

        <section aria-live="polite">
          <div className="results-bar">
            <span className="count">{shown ? `${shown.length} ${shown.length === 1 ? 'PG' : 'PGs'}${coords ? ', nearest first' : ''}` : 'Searching…'}</span>
            <div className="view-toggle" role="group" aria-label="View">
              <button className={view === 'list' ? 'on' : ''} aria-pressed={view === 'list'} onClick={() => patch({ view: '' })}><ListIcon width={16} height={16} /> List</button>
              <button className={view === 'map' ? 'on' : ''} aria-pressed={view === 'map'} onClick={() => patch({ view: 'map' })}><MapIcon width={16} height={16} /> Map</button>
            </div>
          </div>
          {tags.length > 0 && (
            <div className="active-tags">
              {tags.map(([text, remove], i) => (
                <span className="tag" key={i}>{text}<button aria-label={`Remove ${text}`} onClick={remove}>×</button></span>
              ))}
            </div>
          )}

          {error && <p className="error-text">Could not load PGs: {error}</p>}
          {!shown ? <SkeletonGrid n={6} /> : shown.length === 0 && !error ? (
            <div className="empty-state">
              <h3>No PGs match these filters</h3>
              <p>Try a higher budget, fewer facilities, or include PGs that have no free beds.</p>
              <button className="btn btn-ghost" onClick={clearAll}>Clear all filters</button>
            </div>
          ) : view === 'map' ? (
            <div className="map-split">
              <PgMap pgs={shown} userPos={coords} selectedId={picked?.id} onSelect={pick} height={620} />
              <div className="map-list">
                {shown.map((pg) => <PgCard key={pg.id} pg={pg} selected={picked?.id === pg.id} onHover={pick} />)}
              </div>
            </div>
          ) : (
            <div className="pg-grid">{shown.map((pg) => <PgCard key={pg.id} pg={pg} />)}</div>
          )}
        </section>
      </div>
    </>
  )
}
