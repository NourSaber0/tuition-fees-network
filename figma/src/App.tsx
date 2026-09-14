import { useState } from 'react'
import Login from './screens/Login'
import Portal from './Portal'
import type { UserRole } from './Portal'

export default function App() {
  const [userRole, setUserRole] = useState<UserRole | null>(null)

  if (!userRole) {
    return <Login onAuthenticated={(role: UserRole) => setUserRole(role)} />
  }
  return <Portal userRole={userRole} onLogout={() => setUserRole(null)} />
}
