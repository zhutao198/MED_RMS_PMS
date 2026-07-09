import { describe, it, expect, vi, beforeEach } from 'vitest'
import { parseToken } from '@/utils/auth'
import { setupPermissionDirective } from '../permission'
import type { App } from 'vue'

vi.mock('@/utils/auth', () => ({
  parseToken: vi.fn()
}))

function mockParseToken(permissions: string[] | null) {
  ;(parseToken as ReturnType<typeof vi.fn>).mockReturnValue(
    permissions ? { permissions } : null
  )
}

describe('v-permission directive', () => {
  let app: App

  beforeEach(() => {
    vi.clearAllMocks()
    app = { directive: vi.fn() } as any
  })

  it('should keep element visible when permission matches', () => {
    mockParseToken(['user:view'])
    const el = document.createElement('div')
    const parent = document.createElement('div')
    parent.appendChild(el)
    const binding = { value: 'user:view', instance: undefined, arg: null, modifiers: {}, oldValue: null }
    setupPermissionDirective(app)
    const hooks = (app.directive as ReturnType<typeof vi.fn>).mock.calls[0][1]
    hooks.mounted(el, binding)
    expect(parent.contains(el)).toBe(true)
  })

  it('should remove element when permission does not match', () => {
    mockParseToken(['user:view'])
    const el = document.createElement('div')
    const parent = document.createElement('div')
    parent.appendChild(el)
    const binding = { value: 'admin:delete', instance: undefined, arg: null, modifiers: {}, oldValue: null }
    setupPermissionDirective(app)
    const hooks = (app.directive as ReturnType<typeof vi.fn>).mock.calls[0][1]
    hooks.mounted(el, binding)
    expect(parent.contains(el)).toBe(false)
  })

  it('should work with multiple permission values (array)', () => {
    mockParseToken(['user:edit'])
    const el = document.createElement('div')
    const parent = document.createElement('div')
    parent.appendChild(el)
    const binding = { value: ['admin:view', 'user:edit'], instance: undefined, arg: null, modifiers: {}, oldValue: null }
    setupPermissionDirective(app)
    const hooks = (app.directive as ReturnType<typeof vi.fn>).mock.calls[0][1]
    hooks.mounted(el, binding)
    expect(parent.contains(el)).toBe(true)
  })

  it('should remove element when no item in array matches', () => {
    mockParseToken(['user:view'])
    const el = document.createElement('div')
    const parent = document.createElement('div')
    parent.appendChild(el)
    const binding = { value: ['admin:view', 'admin:delete'], instance: undefined, arg: null, modifiers: {}, oldValue: null }
    setupPermissionDirective(app)
    const hooks = (app.directive as ReturnType<typeof vi.fn>).mock.calls[0][1]
    hooks.mounted(el, binding)
    expect(parent.contains(el)).toBe(false)
  })

  it('should remove element when no permissions in storage (null token)', () => {
    mockParseToken(null)
    const el = document.createElement('div')
    const parent = document.createElement('div')
    parent.appendChild(el)
    const binding = { value: 'user:view', instance: undefined, arg: null, modifiers: {}, oldValue: null }
    setupPermissionDirective(app)
    const hooks = (app.directive as ReturnType<typeof vi.fn>).mock.calls[0][1]
    hooks.mounted(el, binding)
    expect(parent.contains(el)).toBe(false)
  })

  it('should remove element when permissions array is empty', () => {
    mockParseToken([])
    const el = document.createElement('div')
    const parent = document.createElement('div')
    parent.appendChild(el)
    const binding = { value: 'user:view', instance: undefined, arg: null, modifiers: {}, oldValue: null }
    setupPermissionDirective(app)
    const hooks = (app.directive as ReturnType<typeof vi.fn>).mock.calls[0][1]
    hooks.mounted(el, binding)
    expect(parent.contains(el)).toBe(false)
  })

  it('should remove element when binding value is empty string', () => {
    mockParseToken(['user:view'])
    const el = document.createElement('div')
    const parent = document.createElement('div')
    parent.appendChild(el)
    const binding = { value: '', instance: undefined, arg: null, modifiers: {}, oldValue: null }
    setupPermissionDirective(app)
    const hooks = (app.directive as ReturnType<typeof vi.fn>).mock.calls[0][1]
    hooks.mounted(el, binding)
    expect(parent.contains(el)).toBe(false)
  })
})