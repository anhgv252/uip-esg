import { Suspense, useMemo } from 'react'
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
import { createPartnerTheme } from '@/theme'
import { AuthProvider } from '@/contexts/AuthContext'
import { TenantConfigProvider, useTenantConfig } from '@/contexts/TenantConfigContext'
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

function ThemedApp() {
  const { config } = useTenantConfig()
  const muiTheme = useMemo(
    () => createPartnerTheme(config?.branding),
    [config?.branding],
  )

  return (
    <ThemeProvider theme={muiTheme}>
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
        <TenantConfigProvider>
          <ThemedApp />
        </TenantConfigProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
