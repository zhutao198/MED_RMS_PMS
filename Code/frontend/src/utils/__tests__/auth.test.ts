import { describe, it, expect, vi, beforeEach } from 'vitest'
import { parseToken, getRoles, hasRole, isLoggedIn } from '../auth'

const VALID_JWT =
  'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbImFkbWluIiwicG0iXSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.fake'
const TOKEN_WITH_SINGLE_ROLE =
  'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwbSIsInJvbGUiOiJwbSIsImlhdCI6MTcwMDAwMDAwMH0.fake'
const MALFORMED_JWT = 'not-a-jwt'

beforeEach(() => {
  localStorage.clear()
})

describe('parseToken', () => {
  it('returns correct payload for a valid JWT', () => {
    const payload = parseToken(VALID_JWT)
    expect(payload).not.toBeNull()
    expect(payload).toHaveProperty('sub', 'admin')
    expect(payload).toHaveProperty('roles')
    expect(Array.isArray(payload!.roles)).toBe(true)
  })

  it('returns null when token is not provided and localStorage is empty', () => {
    expect(parseToken()).toBeNull()
  })

  it('reads token from localStorage when no argument is given', () => {
    localStorage.setItem('token', VALID_JWT)
    const payload = parseToken()
    expect(payload).not.toBeNull()
    expect(payload).toHaveProperty('sub', 'admin')
  })

  it('returns null for a malformed token', () => {
    expect(parseToken(MALFORMED_JWT)).toBeNull()
  })

  it('returns null for a token with missing payload part', () => {
    expect(parseToken('header..signature')).toBeNull()
  })

  it('returns null for an empty string', () => {
    expect(parseToken('')).toBeNull()
  })

  it('returns null when token is null', () => {
    expect(parseToken(null as unknown as string)).toBeNull()
  })
})

describe('getRoles', () => {
  it('returns roles array from token with roles array', () => {
    const roles = getRoles(VALID_JWT)
    expect(roles).toEqual(['admin', 'pm'])
  })

  it('returns single role wrapped in array when token has role field', () => {
    const roles = getRoles(TOKEN_WITH_SINGLE_ROLE)
    expect(roles).toEqual(['pm'])
  })

  it('returns empty array when token has no roles', () => {
    const noRoleToken =
      'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ2aWV3ZXIifQ.fake'
    expect(getRoles(noRoleToken)).toEqual([])
  })

  it('returns empty array for invalid token', () => {
    expect(getRoles(MALFORMED_JWT)).toEqual([])
  })

  it('returns empty array when token is missing', () => {
    expect(getRoles()).toEqual([])
  })

  it('reads token from localStorage when no argument is given', () => {
    localStorage.setItem('token', VALID_JWT)
    expect(getRoles()).toEqual(['admin', 'pm'])
  })
})

describe('hasRole', () => {
  it('returns true when user has one of the required roles', () => {
    expect(hasRole(['admin'], VALID_JWT)).toBe(true)
  })

  it('returns true when user has any of multiple required roles', () => {
    expect(hasRole(['reviewer', 'pm'], VALID_JWT)).toBe(true)
  })

  it('returns false when user has none of the required roles', () => {
    expect(hasRole(['reviewer', 'compliance'], VALID_JWT)).toBe(false)
  })

  it('returns false for empty required array', () => {
    expect(hasRole([], VALID_JWT)).toBe(false)
  })

  it('returns false for invalid token', () => {
    expect(hasRole(['admin'], MALFORMED_JWT)).toBe(false)
  })

  it('returns false when token is missing', () => {
    expect(hasRole(['admin'])).toBe(false)
  })

  it('reads token from localStorage when no token argument is given', () => {
    localStorage.setItem('token', VALID_JWT)
    expect(hasRole(['admin'])).toBe(true)
  })
})

describe('isLoggedIn', () => {
  it('returns true when token exists in localStorage', () => {
    localStorage.setItem('token', VALID_JWT)
    expect(isLoggedIn()).toBe(true)
  })

  it('returns false when localStorage is empty', () => {
    expect(isLoggedIn()).toBe(false)
  })

  it('returns false when token is empty string', () => {
    localStorage.setItem('token', '')
    expect(isLoggedIn()).toBe(false)
  })

  it('returns false when token is removed', () => {
    localStorage.setItem('token', VALID_JWT)
    localStorage.removeItem('token')
    expect(isLoggedIn()).toBe(false)
  })
})