import { useEffect, useState } from 'react'
import { AuthContext } from './authStore.js'
import {
  authenticate,
  clearSession,
  createDemoAccount,
  getSession,
  requestOtpCode,
  requestRegistrationOtp as requestRegOtp,
  verifyOtpCode,
  verifyRegistrationOtp as verifyRegOtp,
} from './authService.js'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(getSession)

  useEffect(() => {
    function handleAuthExpired() {
      clearSession()
      setUser(null)
    }
    window.addEventListener('smartdesk:auth-expired', handleAuthExpired)
    return () => window.removeEventListener('smartdesk:auth-expired', handleAuthExpired)
  }, [])

  async function requestOtp(email) {
    return await requestOtpCode(email)
  }

  async function verifyOtp(email, otp, role) {
    const authenticatedUser = await verifyOtpCode(email, otp, role)
    setUser(authenticatedUser)
    return authenticatedUser
  }

  async function requestRegistrationOtp(values) {
    return await requestRegOtp(values)
  }

  async function verifyRegistrationOtp(email, otp, role) {
    const authenticatedUser = await verifyRegOtp(email, otp, role)
    setUser(authenticatedUser)
    return authenticatedUser
  }

  function signIn(email, password, role) {
    const authenticatedUser = authenticate(email, password, role)
    setUser(authenticatedUser)
    return authenticatedUser
  }

  function signUp(values) {
    const newUser = createDemoAccount(values)
    setUser(newUser)
    return newUser
  }

  function signOut() {
    clearSession()
    setUser(null)
  }

  const value = {
    user,
    requestOtp,
    verifyOtp,
    requestRegistrationOtp,
    verifyRegistrationOtp,
    signIn,
    signUp,
    signOut,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
