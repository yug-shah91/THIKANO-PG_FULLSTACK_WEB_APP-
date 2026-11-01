import { useEffect, useState } from 'react'
import { inspectionApi } from '../api/services'

// Photos are private, so <img src="..."> can not be used (it sends no login token).
// We download the photo with our token and show it from memory.
export default function AuthedImage({ photoId, alt = 'Room photo', onClick }) {
  const [src, setSrc] = useState(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let url
    let cancelled = false
    inspectionApi.photo(photoId)
      .then((r) => { if (!cancelled) { url = URL.createObjectURL(r.data); setSrc(url) } })
      .catch(() => { if (!cancelled) setFailed(true) })
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url) }
  }, [photoId])

  if (failed) return <div className="photo photo-empty">Could not load</div>
  if (!src) return <div className="photo photo-empty">…</div>
  return <img className="photo" src={src} alt={alt} onClick={onClick} />
}
