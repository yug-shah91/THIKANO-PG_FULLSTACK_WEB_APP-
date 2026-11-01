import { useEffect, useMemo, useState } from 'react'
import { visitApi } from '../api/services'
import { errMsg } from '../api/axios'

const DAYS_AHEAD = 14
const iso = (d) => d.toLocaleDateString('en-CA') // YYYY-MM-DD in the user's timezone
const hhmm = (t) => t.slice(0, 5)                // "10:00:00" -> "10:00"
const nice = (t) => {
  const [h] = t.split(':').map(Number)
  return `${h % 12 || 12}:00 ${h < 12 ? 'AM' : 'PM'}`
}

// Pick a day (next 14 days) and an hourly slot. Slots that already passed today are greyed out.
export default function VisitPicker({ onPick }) {
  const [slots, setSlots] = useState([])
  const [error, setError] = useState('')
  const [day, setDay] = useState(null)
  const [time, setTime] = useState(null)

  useEffect(() => {
    visitApi.slots().then((r) => setSlots(r.data.map(hhmm))).catch((e) => setError(errMsg(e)))
  }, [])

  const days = useMemo(() => Array.from({ length: DAYS_AHEAD + 1 }, (_, i) => {
    const d = new Date(); d.setDate(d.getDate() + i); return d
  }), [])

  const tooSoon = (t) => {
    if (!day || iso(day) !== iso(new Date())) return false
    const when = new Date(day); const [h, m] = t.split(':').map(Number)
    when.setHours(h, m, 0, 0)
    return when.getTime() < Date.now() + 60 * 60 * 1000 // needs at least 1 hour notice
  }

  const pickDay = (d) => { setDay(d); setTime(null); onPick(null) }
  const pickTime = (t) => { setTime(t); onPick({ visitDate: iso(day), visitTime: t }) }

  if (error) return <p className="error-text">{error}</p>
  return (
    <>
      <div className="days" role="listbox" aria-label="Pick a day">
        {days.map((d) => (
          <button type="button" key={iso(d)} className={`day ${day && iso(day) === iso(d) ? 'on' : ''}`} onClick={() => pickDay(d)}>
            <small>{d.toLocaleDateString('en-IN', { weekday: 'short' })}</small>
            <strong>{d.getDate()}</strong>
            <small>{d.toLocaleDateString('en-IN', { month: 'short' })}</small>
          </button>
        ))}
      </div>
      {day ? (
        <div className="slots">
          {slots.map((t) => (
            <button type="button" key={t} className={`slot ${time === t ? 'on' : ''}`} disabled={tooSoon(t)} onClick={() => pickTime(t)}>
              {nice(t)}
            </button>
          ))}
        </div>
      ) : <p className="muted small">Pick a day to see the times.</p>}
    </>
  )
}
