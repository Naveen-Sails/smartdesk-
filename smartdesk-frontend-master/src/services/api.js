import axios from 'axios'

export const TOKEN_STORAGE_KEY = 'smartdesk_access_token'

export function getAuthToken() {
  try {
    return window.localStorage.getItem(TOKEN_STORAGE_KEY)
  } catch {
    return null
  }
}

export function setAuthToken(token) {
  try {
    if (token) {
      window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
    } else {
      window.localStorage.removeItem(TOKEN_STORAGE_KEY)
    }
  } catch {
    // Storage access fallback
  }
}

export function clearAuthToken() {
  try {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY)
  } catch {
    // Storage access fallback
  }
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request Interceptor: Attach JWT Bearer Token
api.interceptors.request.use(
  (config) => {
    const token = getAuthToken()
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response Interceptor: Handle 401 Token Expiration / Auth Failures
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      const isAuthRoute = error.config?.url?.includes('/auth/') || error.config?.url?.includes('/access-requests/')
      if (!isAuthRoute) {
        // Clear expired token and notify session
        clearAuthToken()
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('smartdesk:auth-expired'))
        }
      }
    }
    return Promise.reject(error)
  }
)

export default api
