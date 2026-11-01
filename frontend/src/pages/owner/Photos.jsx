import { useEffect, useState } from 'react'
import { pgPhotoApi } from '../../api/services'
import { errMsg } from '../../api/axios'
import { useToast } from '../../context/ToastContext'
import { PageHeader, Skeleton, Empty } from '../../components/UI'

const MAX = 8

export default function Photos() {
  const toast = useToast()
  const [photos, setPhotos] = useState(null)
  const [busy, setBusy] = useState(false)
  const [over, setOver] = useState(false)

  useEffect(() => {
    pgPhotoApi.list().then((r) => setPhotos(r.data)).catch((e) => { toast(errMsg(e), 'error'); setPhotos([]) })
  }, [])

  // one request at a time, so the 8 photo limit is checked in order
  const upload = async (files) => {
    if (busy) return
    setBusy(true)
    try {
      for (const file of files) {
        const fd = new FormData()
        fd.append('file', file)
        const r = await pgPhotoApi.add(fd)
        setPhotos(r.data)
      }
      toast('Photo added')
    } catch (e) { toast(errMsg(e), 'error') } finally { setBusy(false) }
  }

  const act = async (fn, okText) => {
    if (busy) return
    setBusy(true)
    try { const r = await fn(); setPhotos(r.data); if (okText) toast(okText) } catch (e) { toast(errMsg(e), 'error') } finally { setBusy(false) }
  }

  const full = photos && photos.length >= MAX

  return (
    <>
      <PageHeader title="Photos" subtitle={`Show your PG off. Up to ${MAX} photos; the first one is the cover on search.`} />

      <div className={`drop ${over ? 'over' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setOver(true) }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); if (!full) upload([...e.dataTransfer.files]) }}>
        <p><strong>Drag photos here</strong> or</p>
        <label className={`btn ${full || busy ? 'btn-ghost' : ''}`} style={{ cursor: full ? 'not-allowed' : 'pointer' }}>
          {busy ? 'Uploading…' : full ? 'Photo limit reached' : 'Choose photos'}
          <input type="file" accept="image/jpeg,image/png,image/webp" multiple hidden disabled={full || busy}
            onChange={(e) => { upload([...e.target.files]); e.target.value = '' }} />
        </label>
        <p className="muted small" style={{ marginTop: 8 }}>
          JPEG, PNG or WebP, up to 5 MB each. Tip: bright daylight shots of the rooms, bathroom, kitchen and the building outside. These photos are public.
        </p>
      </div>

      {!photos ? <Skeleton h={160} r={14} style={{ marginTop: 16 }} /> : photos.length === 0 ? <Empty text="No photos yet. Listings with photos get far more bookings." /> : (
        <div className="photo-admin">
          {photos.map((p, i) => (
            <figure key={p.id}>
              {i === 0 && <span className="cover-tag">COVER</span>}
              <img src={`/api/public/pg-photos/${p.id}`} alt={`Photo ${i + 1}`} />
              <figcaption>
                {i !== 0 && <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => act(() => pgPhotoApi.cover(p.id), 'Cover updated')}>Make cover</button>}
                <button className="btn btn-ghost btn-sm" disabled={busy}
                  onClick={() => window.confirm('Remove this photo?') && act(() => pgPhotoApi.remove(p.id), 'Photo removed')}>Remove</button>
              </figcaption>
            </figure>
          ))}
        </div>
      )}
    </>
  )
}
