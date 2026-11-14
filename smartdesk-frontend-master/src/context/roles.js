export const ROLES = {
  EMPLOYEE: 'EMPLOYEE',
  AGENT: 'AGENT',
  MANAGER: 'MANAGER',
  ADMIN: 'ADMIN',
}

export function normalizeRole(role) {
  if (!role) return ROLES.EMPLOYEE
  const cleaned = role.replace(/^ROLE_/, '').toUpperCase()
  return ROLES[cleaned] || cleaned
}

export function toBackendRole(role) {
  if (!role) return 'ROLE_EMPLOYEE'
  if (role.startsWith('ROLE_')) return role
  return `ROLE_${role.toUpperCase()}`
}
