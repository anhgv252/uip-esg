import { describe, it, expect } from 'vitest'
import { renderHook } from '@testing-library/react'
import React from 'react'
import {
  TenantConfigContext,
  useTenantConfig,
  useFeatureFlag,
} from '@/contexts/TenantConfigContext'
import type { TenantConfig } from '@/api/tenantConfig'

interface TenantConfigContextValue {
  config: TenantConfig | null
  isLoading: boolean
  isFeatureEnabled: (flag: string) => boolean
}

function makeConfigCtx(
  config: TenantConfig | null,
  isLoading = false,
): TenantConfigContextValue {
  return {
    config,
    isLoading,
    isFeatureEnabled: (flag: string) => config?.features[flag]?.enabled ?? true,
  }
}

const sampleConfig = (features: Record<string, { enabled: boolean }> = {}): TenantConfig => ({
  tenantId: 'hcm',
  features,
  branding: { partnerName: 'HCMC', primaryColor: '#1976d2', logoUrl: null },
})

function wrapperWith(ctxValue: TenantConfigContextValue) {
  return ({ children }: { children: React.ReactNode }) => (
    <TenantConfigContext.Provider value={ctxValue}>{children}</TenantConfigContext.Provider>
  )
}

describe('useTenantConfig', () => {
  it('throws when used outside TenantConfigProvider', () => {
    expect(() => renderHook(() => useTenantConfig())).toThrow(
      'useTenantConfig must be used within TenantConfigProvider',
    )
  })

  it('returns context value when inside provider', () => {
    const ctx = makeConfigCtx(sampleConfig())

    const { result } = renderHook(() => useTenantConfig(), { wrapper: wrapperWith(ctx) })
    expect(result.current.config?.tenantId).toBe('hcm')
  })

  it('returns isLoading from context', () => {
    const ctx = makeConfigCtx(null, true)
    const { result } = renderHook(() => useTenantConfig(), { wrapper: wrapperWith(ctx) })
    expect(result.current.isLoading).toBe(true)
  })
})

describe('isFeatureEnabled', () => {
  it('returns true when flag is enabled', () => {
    const ctx = makeConfigCtx(sampleConfig({ tenant_management: { enabled: true } }))
    const { result } = renderHook(() => useTenantConfig(), { wrapper: wrapperWith(ctx) })
    expect(result.current.isFeatureEnabled('tenant_management')).toBe(true)
  })

  it('returns false when flag is explicitly disabled', () => {
    const ctx = makeConfigCtx(sampleConfig({ tenant_management: { enabled: false } }))
    const { result } = renderHook(() => useTenantConfig(), { wrapper: wrapperWith(ctx) })
    expect(result.current.isFeatureEnabled('tenant_management')).toBe(false)
  })

  it('returns true (fail-open) when flag is not present in config', () => {
    const ctx = makeConfigCtx(sampleConfig())
    const { result } = renderHook(() => useTenantConfig(), { wrapper: wrapperWith(ctx) })
    expect(result.current.isFeatureEnabled('unknown_flag')).toBe(true)
  })

  it('returns true (fail-open) when config is null', () => {
    const ctx = makeConfigCtx(null)
    const { result } = renderHook(() => useTenantConfig(), { wrapper: wrapperWith(ctx) })
    expect(result.current.isFeatureEnabled('any_flag')).toBe(true)
  })

  it('handles mixed enabled/disabled/missing flags', () => {
    const ctx = makeConfigCtx(sampleConfig({
      enabled_flag: { enabled: true },
      disabled_flag: { enabled: false },
    }))
    const { result } = renderHook(() => useTenantConfig(), { wrapper: wrapperWith(ctx) })
    expect(result.current.isFeatureEnabled('enabled_flag')).toBe(true)
    expect(result.current.isFeatureEnabled('disabled_flag')).toBe(false)
    expect(result.current.isFeatureEnabled('missing_flag')).toBe(true)
  })
})

describe('useFeatureFlag', () => {
  it('returns true when flag is enabled', () => {
    const ctx = makeConfigCtx(sampleConfig({ ai_workflow: { enabled: true } }))
    const { result } = renderHook(() => useFeatureFlag('ai_workflow'), { wrapper: wrapperWith(ctx) })
    expect(result.current).toBe(true)
  })

  it('returns false when flag is disabled', () => {
    const ctx = makeConfigCtx(sampleConfig({ ai_workflow: { enabled: false } }))
    const { result } = renderHook(() => useFeatureFlag('ai_workflow'), { wrapper: wrapperWith(ctx) })
    expect(result.current).toBe(false)
  })

  it('returns true (fail-open) for missing flag with null config', () => {
    const ctx = makeConfigCtx(null)
    const { result } = renderHook(() => useFeatureFlag('missing'), { wrapper: wrapperWith(ctx) })
    expect(result.current).toBe(true)
  })

  it('propagates context error when used outside provider', () => {
    expect(() => renderHook(() => useFeatureFlag('test'))).toThrow(
      'useTenantConfig must be used within TenantConfigProvider',
    )
  })
})
