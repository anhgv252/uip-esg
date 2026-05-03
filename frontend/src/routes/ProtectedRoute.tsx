import { type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { CircularProgress, Box } from '@mui/material'
import { useAuth } from '@/hooks/useAuth'
import { type UserRole } from '@/contexts/AuthContext'

interface ProtectedRouteProps {
  children: ReactNode
  requiredRole?: UserRole
  requiredRoles?: UserRole[]
  requiredScope?: string
}

export default function ProtectedRoute({
  children,
  requiredRole,
  requiredRoles,
  requiredScope,
}: ProtectedRouteProps) {
  const { isAuthenticated, isLoading, user } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight="100vh"
      >
        <CircularProgress />
      </Box>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  // Single role check (backward compatible)
  if (requiredRole && user?.role !== requiredRole) {
    return <Navigate to="/dashboard" replace />
  }

  // Multiple roles check — user must have at least one matching role
  if (requiredRoles?.length && !requiredRoles.includes(user?.role as UserRole)) {
    return <Navigate to="/dashboard" replace />
  }

  // Scope check — user must have the required scope in their scopes array
  if (requiredScope && !user?.scopes?.includes(requiredScope)) {
    return <Navigate to="/dashboard" replace />
  }

  return <>{children}</>
}
