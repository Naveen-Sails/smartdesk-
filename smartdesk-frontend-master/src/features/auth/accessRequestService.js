import api from '../../services/api.js'

export async function fetchPendingAccessRequests() {
  try {
    const res = await api.get('/v1/access-requests/pending')
    return Array.isArray(res.data) ? res.data : []
  } catch (error) {
    console.debug('Failed to fetch access requests from backend:', error.message)
    return []
  }
}

export async function fetchActiveDepartmentStaff() {
  try {
    const res = await api.get('/v1/access-requests/staff')
    return Array.isArray(res.data) ? res.data : []
  } catch (error) {
    console.debug('Failed to fetch department staff from backend:', error.message)
    return []
  }
}

export async function approveAccessRequest(requestId) {
  const res = await api.post(`/v1/access-requests/${requestId}/approve`)
  return res.data
}

export async function rejectAccessRequest(requestId, reason = '') {
  const res = await api.post(`/v1/access-requests/${requestId}/reject`, { reason })
  return res.data
}

export async function fetchMyPendingRequest() {
  try {
    const res = await api.get('/v1/access-requests/my-pending')
    if (res.data && res.data.id) {
      return res.data
    }
    return null
  } catch (error) {
    return null
  }
}

export async function refreshUserProfile() {
  try {
    const res = await api.get('/v1/auth/me')
    return res.data
  } catch {
    return null
  }
}

export async function submitRoleUpgradeRequest(requestedRole, department) {
  const res = await api.post('/v1/access-requests/request', {
    requestedRole,
    department,
  })
  return res.data
}
