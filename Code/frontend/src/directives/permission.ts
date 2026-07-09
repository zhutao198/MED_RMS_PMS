import type { App } from 'vue'
import { parseToken } from '@/utils/auth'

function getUserPermissions(): string[] {
  const p = parseToken()
  if (!p) return []
  const perms = p['permissions']
  if (Array.isArray(perms)) return perms.map(String)
  return []
}

function hasPermission(value: string | string[]): boolean {
  const perms = getUserPermissions()
  if (!perms.length) return false
  const required = Array.isArray(value) ? value : [value]
  return required.some(r => perms.includes(r))
}

export function setupPermissionDirective(app: App) {
  app.directive('permission', {
    mounted(el: HTMLElement, binding) {
      if (!hasPermission(binding.value)) {
        el.parentNode?.removeChild(el)
      }
    }
  })
}
