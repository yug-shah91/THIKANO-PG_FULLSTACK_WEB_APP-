import { BuildingIcon } from './Icons'

// Card image: the owner's cover photo when there is one. Without a photo, a quiet tonal block
// (no illustration) that says plainly there is no photo yet.
export default function PgVisual({ pg, children }) {
  // the .pg-visual box gives every card the same 4:3 frame
  return (
    <div className="pg-visual">
      {pg.coverPhotoId
        ? <img className="card-photo" src={`/api/public/pg-photos/${pg.coverPhotoId}`} alt={pg.name} loading="lazy" />
        : <div className="no-photo"><BuildingIcon width={34} height={34} /><span>No photo yet</span></div>}
      {children}
    </div>
  )
}
