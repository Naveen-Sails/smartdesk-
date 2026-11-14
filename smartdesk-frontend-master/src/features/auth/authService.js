import { ROLES, normalizeRole, toBackendRole } from '../../context/roles.js'
import api, { clearAuthToken, setAuthToken } from '../../services/api.js'

const SESSION_KEY = 'smartdesk-demo-session'
const ACCOUNT_KEY = 'smartdesk-demo-accounts'

export const DEPARTMENTS = ['IT', 'HR', 'Facilities', 'Java', 'Dotnet', 'AI']
const DEPARTMENT_ROLES = [ROLES.AGENT, ROLES.MANAGER]

export const DEMO_CREDENTIALS = []

function readStorage(key, fallback) {
  try {
    const value = window.localStorage.getItem(key)
    return value ? JSON.parse(value) : fallback
  } catch {
    return fallback
  }
}

function writeStorage(key, value) {
  try {
    window.localStorage.setItem(key, JSON.stringify(value))
  } catch {
    throw new Error('Browser storage is unavailable. Enable local storage to use SmartDesk.')
  }
}

export function getSession() {
  const session = readStorage(SESSION_KEY, null)
  if (!session || !session.email || !session.name || !Object.values(ROLES).includes(session.role)) return null
  if (DEPARTMENT_ROLES.includes(session.role) && !session.department) return { ...session, department: 'IT' }
  return session
}

export function getDemoCredentials(role, department = 'IT') {
  return DEMO_CREDENTIALS.find((account) => account.role === role && (role !== ROLES.AGENT || account.department === department)) || null
}

/**
 * Step 1: Request 6-digit OTP code for Login
 */
export async function requestOtpCode(email) {
  const normalizedEmail = (email || '').trim().toLowerCase()
  if (!normalizedEmail) {
    throw new Error('Please enter a valid email address.')
  }

  try {
    const response = await api.post('/v1/auth/otp/request', { email: normalizedEmail })
    return response.data
  } catch (error) {
    const backendMessage = error.response?.data?.message
    if (backendMessage) {
      throw new Error(backendMessage)
    }
    if (error.code === 'ERR_NETWORK') {
      throw new Error('Cannot reach API Gateway at port 8080. Please ensure the backend is running.')
    }
    throw new Error('Failed to request verification code. Please try again.')
  }
}

/**
 * Step 2: Verify OTP code for Login and receive JWT
 */
export async function verifyOtpCode(email, otp, expectedRole) {
  const normalizedEmail = (email || '').trim().toLowerCase()
  const sanitizedOtp = (otp || '').trim()

  if (!normalizedEmail) {
    throw new Error('Email is required.')
  }
  if (!/^\d{6}$/.test(sanitizedOtp)) {
    throw new Error('Verification code must be 6 numeric digits.')
  }

  try {
    const response = await api.post('/v1/auth/otp/verify', {
      email: normalizedEmail,
      otp: sanitizedOtp,
    })

    const { accessToken, user } = response.data
    if (!accessToken || !user) {
      throw new Error('Invalid response received from authentication server.')
    }

    setAuthToken(accessToken)

    const normalizedRole = normalizeRole(user.role)
    const department = user.department || (DEPARTMENT_ROLES.includes(normalizedRole) ? 'IT' : undefined)

    const session = {
      id: user.id,
      name: user.fullName || user.email.split('@')[0],
      email: user.email,
      role: normalizedRole,
      ...(department ? { department } : {}),
    }

    writeStorage(SESSION_KEY, session)
    return session
  } catch (error) {
    const backendMessage = error.response?.data?.message
    if (backendMessage) {
      throw new Error(backendMessage)
    }
    if (error.code === 'ERR_NETWORK') {
      throw new Error('Cannot reach API Gateway at port 8080. Please check your backend connection.')
    }
    throw new Error('Verification failed. Please check your code and try again.')
  }
}

/**
 * Registration Step 1: Request 6-digit OTP code for New User Registration
 */
export async function requestRegistrationOtp({ name, email, role, department }) {
  const normalizedEmail = (email || '').trim().toLowerCase()
  const sanitizedName = (name || '').trim()

  if (!sanitizedName) {
    throw new Error('Full name is required.')
  }
  if (!normalizedEmail) {
    throw new Error('Email address is required.')
  }

  try {
    const response = await api.post('/v1/auth/register/request', {
      fullName: sanitizedName,
      email: normalizedEmail,
      role: toBackendRole(role),
      department: department || 'IT',
    })
    return response.data
  } catch (error) {
    const backendMessage = error.response?.data?.message
    if (backendMessage) {
      throw new Error(backendMessage)
    }
    if (error.code === 'ERR_NETWORK') {
      throw new Error('Cannot reach API Gateway at port 8080. Please ensure the backend is running.')
    }
    throw new Error('Failed to request registration code. Please try again.')
  }
}

/**
 * Registration Step 2: Verify Registration OTP and activate user account
 */
export async function verifyRegistrationOtp(email, otp, expectedRole) {
  const normalizedEmail = (email || '').trim().toLowerCase()
  const sanitizedOtp = (otp || '').trim()

  if (!normalizedEmail) {
    throw new Error('Email is required.')
  }
  if (!/^\d{6}$/.test(sanitizedOtp)) {
    throw new Error('Verification code must be 6 numeric digits.')
  }

  try {
    const response = await api.post('/v1/auth/register/verify', {
      email: normalizedEmail,
      otp: sanitizedOtp,
    })

    const { accessToken, user } = response.data
    if (!accessToken || !user) {
      throw new Error('Invalid response received from authentication server.')
    }

    setAuthToken(accessToken)

    const normalizedRole = normalizeRole(user.role)
    const department = user.department || (DEPARTMENT_ROLES.includes(normalizedRole) ? 'IT' : undefined)

    const session = {
      id: user.id,
      name: user.fullName || user.email.split('@')[0],
      email: user.email,
      role: normalizedRole,
      ...(department ? { department } : {}),
    }

    writeStorage(SESSION_KEY, session)
    return session
  } catch (error) {
    const backendMessage = error.response?.data?.message
    if (backendMessage) {
      throw new Error(backendMessage)
    }
    if (error.code === 'ERR_NETWORK') {
      throw new Error('Cannot reach API Gateway at port 8080. Please check your backend connection.')
    }
    throw new Error('Registration verification failed. Please check your code.')
  }
}

/**
 * Demo Password Fallback Authenticator (for offline / mockup testing)
 */
export function authenticate(email, password, expectedRole) {
  const normalizedEmail = email.trim().toLowerCase()
  const storedAccounts = readStorage(ACCOUNT_KEY, [])
  const demoAccount = DEMO_CREDENTIALS.find((account) => account.email === normalizedEmail)
  const customAccount = Array.isArray(storedAccounts)
    ? storedAccounts.find((account) => account.email === normalizedEmail)
    : null
  const account = demoAccount || customAccount

  if (!account || account.password !== password || account.role !== expectedRole) {
    throw new Error(`Those credentials do not match a ${expectedRole.toLowerCase()} account.`)
  }

  const department = account.department || (DEPARTMENT_ROLES.includes(account.role) ? 'IT' : undefined)
  const session = { name: account.name, email: account.email, role: account.role, ...(department ? { department } : {}) }
  writeStorage(SESSION_KEY, session)
  return session
}

export function createDemoAccount({ name, email, password, role, department = 'IT' }) {
  const normalizedEmail = email.trim().toLowerCase()
  const storedAccounts = readStorage(ACCOUNT_KEY, [])
  const accounts = Array.isArray(storedAccounts) ? storedAccounts : []
  const demoEmailExists = DEMO_CREDENTIALS.some((account) => account.email === normalizedEmail)

  if (!name.trim() || !normalizedEmail || !password || !Object.values(ROLES).includes(role)) {
    throw new Error('Enter your name, email, password, and a valid workspace.')
  }
  if (password.length < 8) throw new Error('Use a password with at least 8 characters.')
  if (demoEmailExists || accounts.some((account) => account.email === normalizedEmail)) {
    throw new Error('An account already exists with that email address.')
  }

  const account = {
    name: name.trim(),
    email: normalizedEmail,
    password,
    role,
    ...(DEPARTMENT_ROLES.includes(role) ? { department } : {}),
  }
  writeStorage(ACCOUNT_KEY, [...accounts, account])
  const session = { name: account.name, email: account.email, role: account.role, ...(account.department ? { department: account.department } : {}) }
  writeStorage(SESSION_KEY, session)
  return session
}

export function clearSession() {
  try {
    clearAuthToken()
    window.localStorage.removeItem(SESSION_KEY)
  } catch {
    // Session memory cleared
  }
}
