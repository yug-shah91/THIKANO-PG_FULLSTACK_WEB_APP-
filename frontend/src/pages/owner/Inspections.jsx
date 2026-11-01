import { Link, useParams } from 'react-router-dom'
import InspectionView from '../../components/InspectionView'
import { PageHeader } from '../../components/UI'

export default function Inspections() {
  const { tenantId } = useParams()
  return (
    <>
      <PageHeader title="Room report" subtitle="Condition of the room at move-in and move-out">
        <Link className="btn btn-ghost btn-sm" to="/owner/tenants">← Tenants</Link>
      </PageHeader>
      <InspectionView tenantId={Number(tenantId)} isOwner />
    </>
  )
}
