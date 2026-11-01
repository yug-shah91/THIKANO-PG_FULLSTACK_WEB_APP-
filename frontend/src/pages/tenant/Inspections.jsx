import InspectionView from '../../components/InspectionView'
import { PageHeader } from '../../components/UI'

export default function Inspections() {
  return (
    <>
      <PageHeader title="Room Report" subtitle="Protect your deposit: note and photograph the room's condition" />
      <InspectionView tenantId={undefined} isOwner={false} />
    </>
  )
}
