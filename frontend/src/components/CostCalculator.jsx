import { useEffect, useState } from 'react'
import { publicApi } from '../api/services'
import { errMsg } from '../api/axios'
import CountUp from './CountUp'
import { money } from './UI'

// "True monthly cost": the server adds rent + required charges + chosen services + commute.
// We only collect the choices and show the answer (it recalculates after every change).
export default function CostCalculator({ pg }) {
  const rooms = (pg.rooms || []).filter((r) => r.monthlyRent != null)
  const charges = pg.charges || []
  const optional = charges.filter((c) => c.kind === 'OPTIONAL_MONTHLY')

  const [roomId, setRoomId] = useState(rooms[0]?.id ?? null)
  const [picked, setPicked] = useState([])
  const [perDay, setPerDay] = useState('')
  const [days, setDays] = useState(22)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!roomId) return
    let stale = false // ignore an answer that arrives after a newer question
    const t = setTimeout(() => {
      publicApi.costEstimate(pg.id, {
        roomId,
        optional: picked,
        commutePerDay: perDay === '' ? undefined : perDay,
        commuteDays: perDay === '' ? undefined : days,
      })
        .then((r) => { if (!stale) { setResult(r.data); setError('') } })
        .catch((e) => { if (!stale) setError(errMsg(e)) })
    }, 250) // wait a moment while the person is still typing
    return () => { stale = true; clearTimeout(t) }
  }, [pg.id, roomId, picked, perDay, days])

  if (rooms.length === 0) return <p className="muted">No priced rooms to estimate yet.</p>

  const toggle = (id) => setPicked(picked.includes(id) ? picked.filter((x) => x !== id) : [...picked, id])

  return (
    <div className="calc">
      <div className="calc-inputs">
        <label className="calc-field">
          <span>Room</span>
          <select value={roomId ?? ''} onChange={(e) => setRoomId(Number(e.target.value))}>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>Room {r.roomNumber} · {money(r.monthlyRent)}{r.bookable ? '' : ' (full)'}</option>
            ))}
          </select>
        </label>

        {optional.length > 0 && (
          <div className="calc-field">
            <span>Services you would use</span>
            <div className="calc-opts">
              {optional.map((c) => (
                <button type="button" key={c.id} className={`chip ${picked.includes(c.id) ? 'on' : ''}`} onClick={() => toggle(c.id)}>
                  {c.name} · {money(c.amount)}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="calc-field">
          <span>Your commute (optional)</span>
          <div className="calc-commute">
            <input type="number" min="0" max="5000" placeholder="₹ per day, both ways" value={perDay} onChange={(e) => setPerDay(e.target.value)} />
            <input type="number" min="0" max="31" value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="days per month" />
            <small className="muted">days / month</small>
          </div>
        </div>
      </div>

      <div className="calc-result">
        {error && <p className="error-text">{error}</p>}
        {result && (
          <>
            <div className="muted small">You would really pay each month</div>
            <div className="calc-total">₹<CountUp key={result.monthlyTotal} value={Math.round(Number(result.monthlyTotal))} ms={500} /></div>
            {Number(result.extraOverRent) > 0 && (
              <div className="calc-extra">+{money(result.extraOverRent)} on top of the {money(result.rent)} rent</div>
            )}
            <ul className="calc-lines">
              <li><span>Room rent</span><b>{money(result.rent)}</b></li>
              {result.requiredCharges.map((c) => <li key={c.id}><span>{c.name} <i>(always)</i></span><b>{money(c.amount)}</b></li>)}
              {result.chosenOptional.map((c) => <li key={c.id}><span>{c.name}</span><b>{money(c.amount)}</b></li>)}
              {Number(result.commute) > 0 && <li><span>Commute</span><b>{money(result.commute)}</b></li>}
            </ul>
            {result.oneTimeCharges.length > 0 && (
              <div className="calc-once">
                <div className="muted small">Paid once, when you move in</div>
                {result.oneTimeCharges.map((c) => <div key={c.id} className="calc-once-row"><span>{c.name}{c.note ? ` (${c.note})` : ''}</span><b>{money(c.amount)}</b></div>)}
                <div className="calc-once-row first"><span>Money needed on day one</span><b>{money(result.firstMonthTotal)}</b></div>
              </div>
            )}
          </>
        )}
        {charges.length === 0 && <p className="muted small" style={{ marginTop: 8 }}>This owner has not listed extra charges. Ask them before you book.</p>}
      </div>
    </div>
  )
}
