import { useAuth } from './useAuth'

/**
 * Checks whether the current user has a specific scope.
 * Scopes come from JWT claims (e.g. "esg:write", "alert:ack").
 */
export function useScope(scope: string): boolean {
  const { user } = useAuth()
  if (!user) return false
  return user.scopes.includes(scope)
}
