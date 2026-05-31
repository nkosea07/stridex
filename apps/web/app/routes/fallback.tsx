import { Navigate } from 'react-router-dom'

export default function FallbackRoute() {
  return <Navigate to="/" replace />
}
