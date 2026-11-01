import { useEffect, useState } from 'react'
import { reviewApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/Modal'
import { Stars } from '../../components/Stars'
import { PageHeader, Field, Loader, Empty, date } from '../../components/UI'

export default function Reviews() {
  const toast = useToast()
  const [items, setItems] = useState(null)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [replying, setReplying] = useState(null)
  const [saving, setSaving] = useState(false)

  const load = () => reviewApi.all(page)
    .then((r) => { setItems(r.data.content); setTotalPages(r.data.totalPages || 1) })
    .catch((e) => { toast(errMsg(e), 'error'); setItems([]) })
  useEffect(() => { setItems(null); load() }, [page])

  const send = async (e) => {
    e.preventDefault()
    if (saving) return
    setSaving(true)
    try {
      await reviewApi.reply(replying.id, { reply: replying.text })
      toast('Reply posted')
      setReplying(null)
      load()
    } catch (err) { toast(errMsg(err), 'error') } finally { setSaving(false) }
  }

  return (
    <>
      <PageHeader title="Reviews" subtitle="What your tenants say. Reply politely: future tenants read both sides." />
      {!items ? <Loader /> : items.length === 0 ? <Empty text="No reviews yet. Tenants can review after staying 7 days." /> : (
        <div className="list">
          {items.map((r) => (
            <div className="card" key={r.id}>
              <div className="row-between">
                <h3>{r.reviewerName}</h3>
                <Stars value={r.rating} size={16} />
              </div>
              <p className="muted small">{date(r.createdAt)}</p>
              {r.comment ? <p>{r.comment}</p> : <p className="muted">No written comment.</p>}
              {r.ownerReply && <p className="reply"><strong>Your reply:</strong> {r.ownerReply}</p>}
              <button className="btn btn-ghost btn-sm" onClick={() => setReplying({ id: r.id, name: r.reviewerName, text: r.ownerReply || '' })}>
                {r.ownerReply ? 'Edit reply' : 'Reply'}
              </button>
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="pager">
          <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>← Prev</button>
          <span>Page {page + 1} of {totalPages}</span>
          <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Next →</button>
        </div>
      )}

      {replying && (
        <Modal title={`Reply to ${replying.name}`} onClose={() => setReplying(null)}>
          <form onSubmit={send}>
            <Field label="Your reply (shown publicly)">
              <textarea rows="4" required maxLength={500} value={replying.text} onChange={(e) => setReplying({ ...replying, text: e.target.value })} />
            </Field>
            <button className="btn btn-block" disabled={saving}>{saving ? 'Posting…' : 'Post reply'}</button>
          </form>
        </Modal>
      )}
    </>
  )
}
