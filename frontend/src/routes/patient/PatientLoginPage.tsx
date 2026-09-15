import { useNavigate } from 'react-router-dom'
import { LoginForm } from '../../features/patient-account/LoginForm'
import { PublicHeader } from '../PublicHeader'

export function PatientLoginPage() {
  const navigate = useNavigate()
  return (
    <div className="min-h-screen bg-gray-50">
      <PublicHeader />
      <div className="flex items-center justify-center px-6 py-12">
        <LoginForm onSuccess={() => navigate('/patient', { replace: true })} />
      </div>
    </div>
  )
}
