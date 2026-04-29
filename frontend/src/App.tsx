import { Suspense } from 'react'
import {
  createBrowserRouter,
  RouterProvider,
  type RouteObject,
} from 'react-router-dom'
import {
  ThemeProvider,
  CssBaseline,
  CircularProgress,
  Box,
} from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { theme } from '@/theme'
import { AuthProvider } from '@/contexts/AuthContext'
import { routes } from '@/routes'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
})

const router = createBrowserRouter(routes as RouteObject[])

function PageFallback() {
  return (
    <Box
      display="flex"
      justifyContent="center"
      alignItems="center"
      minHeight="50vh"
    >
      <CircularProgress />
    </Box>
  )
}

/**
 * ThemedApp sits inside AuthProvider (and future TenantConfigProvider)
 * so theme can be overridden by tenant branding at runtime.
 * Sprint 2 will add TenantConfigProvider above ThemeProvider.
 */
function ThemedApp() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Suspense fallback={<PageFallback />}>
        <RouterProvider router={router} />
      </Suspense>
    </ThemeProvider>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ThemedApp />
      </AuthProvider>
    </QueryClientProvider>
  )
}
