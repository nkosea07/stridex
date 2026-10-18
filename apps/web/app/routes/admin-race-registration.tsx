import { RegistrationFieldsPage } from '@/pages/RegistrationFieldsPage'
import { RequireAdminRoute } from '@/components/auth/RequireAdminRoute'

export default function AdminRaceRegistrationRoute() {
  return (
    <RequireAdminRoute>
      <RegistrationFieldsPage />
    </RequireAdminRoute>
  )
}
