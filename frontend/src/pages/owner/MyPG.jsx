import { useEffect, useState } from 'react'
import { useBusy } from '../../hooks'
import { pgApi, bookingApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import { PageHeader, Field, Loader } from '../../components/UI'

const EMPTY = { name: '', address: '', city: '', description: '', contactPhone: '', latitude: '', longitude: '', baseMonthlyRent: '', rentDueDay: 5, penaltyPercent: 5, bookingMode: 'INSTANT', genderPolicy: 'ANY' }

// One page handles both "create my PG" (first time) and "edit my PG".
export default function MyPG() {
  const [form, setForm] = useState(EMPTY)
  const [exists, setExists] = useState(false)
  const [loading, setLoading] = useState(true)
  const toast = useToast()
  const [busy, run] = useBusy()
  const [waiting, setWaiting] = useState(0)

  useEffect(() => {
    bookingApi.pendingCount().then((r) => setWaiting(r.data.count)).catch(() => {})
    pgApi.get()
      .then((r) => { setForm({ ...EMPTY, ...r.data }); setExists(true) })
      .catch(() => setExists(false)) // no PG yet -> show empty form
      .finally(() => setLoading(false))
  }, [])

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  // Fills latitude/longitude from the browser. Press it while you are AT the PG.
  const useMyLocation = () => {
    if (!navigator.geolocation) { toast('Your browser does not support location', 'error'); return }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setForm((f) => ({ ...f, latitude: pos.coords.latitude.toFixed(6), longitude: pos.coords.longitude.toFixed(6) }))
        toast('Location filled in. Press Save to keep it.')
      },
      () => toast('Could not get your location. Allow it in the browser, or type the numbers.', 'error'),
      { timeout: 10000 }
    )
  }

  const submit = (e) => { e.preventDefault(); return run(() => submitNow(e)) }
  const submitNow = async (e) => {
    e.preventDefault()
    const payload = {
      name: form.name, address: form.address, city: form.city,
      description: form.description, contactPhone: form.contactPhone,
      latitude: form.latitude === '' || form.latitude == null ? null : Number(form.latitude),
      longitude: form.longitude === '' || form.longitude == null ? null : Number(form.longitude),
      baseMonthlyRent: Number(form.baseMonthlyRent),
      rentDueDay: Number(form.rentDueDay), penaltyPercent: Number(form.penaltyPercent),
      bookingMode: form.bookingMode,
      genderPolicy: form.genderPolicy || 'ANY',
    }
    try {
      const { data } = exists ? await pgApi.update(payload) : await pgApi.create(payload)
      setForm({ ...EMPTY, ...data })
      setExists(true)
      toast(exists ? 'PG updated' : 'PG created')
    } catch (err) {
      toast(errMsg(err), 'error')
    }
  }

  if (loading) return <Loader />

  return (
    <>
      <PageHeader title="My PG" subtitle={exists ? 'Update your PG details' : 'Add your PG to get started'} />
      <form className="card form-grid" onSubmit={submit}>
        <Field label="PG name"><input name="name" value={form.name} onChange={change} required /></Field>
        <Field label="City"><input name="city" value={form.city} onChange={change} required /></Field>
        <Field label="Address"><input name="address" value={form.address} onChange={change} required /></Field>
        <Field label="Contact phone"><input name="contactPhone" value={form.contactPhone || ''} onChange={change} /></Field>
        <Field label="Base monthly rent (₹)">
          <input name="baseMonthlyRent" type="number" min="1" value={form.baseMonthlyRent} onChange={change} required />
        </Field>
        <Field label="Rent due day (of month)">
          <input name="rentDueDay" type="number" min="1" max="28" value={form.rentDueDay} onChange={change} />
        </Field>
        <Field label="Late penalty (%)">
          <input name="penaltyPercent" type="number" min="0" max="100" value={form.penaltyPercent} onChange={change} />
        </Field>
        <Field label="How tenants book">
          <select name="bookingMode" value={form.bookingMode || 'INSTANT'} onChange={change}>
            <option value="INSTANT">Instantly (a free bed is booked at once)</option>
            <option value="ON_REQUEST">On request (I approve each booking)</option>
          </select>
        </Field>
        <Field label="Who can stay">
          <select name="genderPolicy" value={form.genderPolicy || 'ANY'} onChange={change}>
            <option value="ANY">Anyone</option>
            <option value="MEN_ONLY">Boys only (men)</option>
            <option value="WOMEN_ONLY">Girls only (women)</option>
          </select>
        </Field>
        <div className="full">
          <p className="muted small" style={{ margin: '-4px 0 8px' }}>
            On request: the bed is held for 48 hours while you decide. If you do not answer, it is released. Changing this affects new bookings only.
            {waiting > 0 && <> <strong>You have {waiting} request{waiting > 1 ? 's' : ''} waiting.</strong> They stay valid after you change this, and you can still approve or decline them under Bookings.</>}
          </p>
          <p className="muted small" style={{ margin: '4px 0 10px' }}>
            Location lets tenants find your PG with “Near me”. Press the button while you are at the PG, or type the numbers (you can copy them from Google Maps).
          </p>
        </div>
        <Field label="Latitude">
          <input name="latitude" type="number" step="any" min="-90" max="90" value={form.latitude ?? ''} onChange={change} />
        </Field>
        <Field label="Longitude">
          <input name="longitude" type="number" step="any" min="-180" max="180" value={form.longitude ?? ''} onChange={change} />
        </Field>
        <div className="full" style={{ marginBottom: 14 }}>
          <button type="button" className="btn btn-ghost btn-sm" onClick={useMyLocation}>Use my current location</button>
        </div>
        <Field label="Description">
          <textarea name="description" rows="3" value={form.description || ''} onChange={change} />
        </Field>
        <div className="full"><button disabled={busy} className="btn">{exists ? 'Save changes' : 'Create PG'}</button></div>
      </form>
    </>
  )
}
