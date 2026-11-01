import { useState } from 'react'
import { useBusy } from '../hooks'
import { reviewApi } from '../api/services'
import { errMsg } from '../api/axios'
import { useToast } from '../context/ToastContext'
import Modal from './Modal'
import { Field } from './UI'
import { StarPicker } from './Stars'

// Write or edit your review. The server checks that you really stayed there.
export default function ReviewModal({ pgName, existing, onClose, onSaved }) {
  const toast = useToast()
  const [busy, run] = useBusy()
  const [rating, setRating] = useState(existing?.rating || 0)
  const [comment, setComment] = useState(existing?.comment || '')
  const [saving, setSaving] = useState(false)

  const save = async (e) => {
    e.preventDefault()
    if (saving || !rating) return
    setSaving(true)
    try {
      await reviewApi.save({ rating, comment: comment || null })
      toast('Thanks! Your review is live.')
      onSaved()
    } catch (err) { toast(errMsg(err), 'error') } finally { setSaving(false) }
  }

  const remove = () => run(() => removeNow())
  const removeNow = async () => {
    if (!window.confirm('Delete your review?')) return
    try { await reviewApi.remove(); toast('Review deleted'); onSaved() } catch (err) { toast(errMsg(err), 'error') }
  }

  return (
    <Modal title={existing ? 'Edit your review' : `Review ${pgName}`} onClose={onClose}>
      <form onSubmit={save}>
        <p className="muted small">Be honest and helpful. Other students will read this. Your name is shown shortened, like “Asha K.”.</p>
        <StarPicker value={rating} onChange={setRating} />
        <Field label="Your experience (optional)">
          <textarea rows="4" maxLength={1000} placeholder="Food, cleanliness, owner, noise, Wi-Fi…" value={comment} onChange={(e) => setComment(e.target.value)} />
        </Field>
        <button className="btn btn-block" disabled={saving || busy || !rating}>{saving ? 'Saving…' : rating ? 'Post review' : 'Pick a star rating'}</button>
        {existing && <button type="button" className="btn btn-ghost btn-block" style={{ marginTop: 8 }} disabled={busy} onClick={remove}>Delete my review</button>}
      </form>
    </Modal>
  )
}
