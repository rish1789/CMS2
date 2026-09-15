// 041-staff-console-pickers FR-001/FR-002: replaces the prior "type a Clinic ID" form with a
// real list of the clinics the signed-in staff member actually belongs to.
import { MyClinicsList } from '../../features/staff-clinics/MyClinicsList'

export function StaffDashboard() {
  return <MyClinicsList />
}
