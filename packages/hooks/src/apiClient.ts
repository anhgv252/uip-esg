import axios from 'axios'

/**
 * Default API client for shared hooks
 * 
 * Configuration:
 * - Browser: Set window.__UIP_API_BASE_URL__ or reads from env
 * - React Native: Set process.env.API_BASE_URL or pass custom client
 * 
 * Example:
 * ```ts
 * // Configure globally in your app entry point:
 * if (typeof window !== 'undefined') {
 *   window.__UIP_API_BASE_URL__ = 'https://api.uip.example.com/api/v1'
 * }
 * 
 * // Or pass a custom client to each hook:
 * const customClient = axios.create({ baseURL: 'https://...' })
 * const { data } = useDashboard(customClient)
 * ```
 */

// Type-safe environment access
const getEnvApiUrl = (): string | undefined => {
  try {
    // Access process.env if available (Node.js/React Native)
    return (globalThis as any).process?.env?.API_BASE_URL
  } catch {
    return undefined
  }
}

export const defaultApiClient = axios.create({
  baseURL:
    (typeof window !== 'undefined' && (window as any).__UIP_API_BASE_URL__) ||
    getEnvApiUrl() ||
    '/api/v1',
  timeout: 10_000,
  withCredentials: true,
})
