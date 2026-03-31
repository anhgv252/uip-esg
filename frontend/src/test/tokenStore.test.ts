import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { tokenStore } from '@/api/client'

// Mock axios — we only test the token store logic here
vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return actual
})

describe('tokenStore', () => {
  beforeEach(() => tokenStore.clear())
  afterEach(() => tokenStore.clear())

  it('returns null when no token set', () => {
    expect(tokenStore.get()).toBeNull()
  })

  it('stores and retrieves access token', () => {
    tokenStore.set('test.jwt.token')
    expect(tokenStore.get()).toBe('test.jwt.token')
  })

  it('clears the token', () => {
    tokenStore.set('abc')
    tokenStore.clear()
    expect(tokenStore.get()).toBeNull()
  })

  it('does not persist to localStorage', () => {
    tokenStore.set('secret')
    expect(window.localStorage.getItem('token')).toBeNull()
    expect(window.sessionStorage.getItem('token')).toBeNull()
  })
})
