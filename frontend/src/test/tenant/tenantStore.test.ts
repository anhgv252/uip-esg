import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { tenantStore, tokenStore } from '@/api/client'

describe('tenantStore', () => {
  beforeEach(() => {
    tokenStore.clear()
  })
  afterEach(() => {
    tokenStore.clear()
  })

  it('returns null by default', () => {
    expect(tenantStore.get()).toBeNull()
  })

  it('stores and retrieves tenant id', () => {
    tenantStore.set('hcm')
    expect(tenantStore.get()).toBe('hcm')
  })

  it('supports setting null to clear', () => {
    tenantStore.set('hcm')
    tenantStore.set(null)
    expect(tenantStore.get()).toBeNull()
  })

  it('is cleared when tokenStore.clear() is called', () => {
    tenantStore.set('hn')
    tokenStore.clear()
    expect(tenantStore.get()).toBeNull()
  })

  it('overwrites previous value', () => {
    tenantStore.set('hcm')
    tenantStore.set('hn')
    expect(tenantStore.get()).toBe('hn')
  })
})
