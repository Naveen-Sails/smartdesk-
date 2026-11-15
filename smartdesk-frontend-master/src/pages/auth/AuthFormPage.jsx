import { useEffect, useState } from 'react'
import { ArrowLeft, ArrowRight, Building2, KeyRound, LockKeyhole, Mail, RotateCw, Send, UserRound } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useAuth } from '../../features/auth/useAuth.js'
import { DEPARTMENTS } from '../../features/auth/authService.js'
import { ROLE_NAVIGATION } from '../../routes/navigation.js'
import { ROLES } from '../../context/roles.js'

function AuthFormPage({ role = ROLES.EMPLOYEE, mode }) {
  const navigate = useNavigate()
  const {
    requestOtp,
    verifyOtp,
    requestRegistrationOtp,
    verifyRegistrationOtp,
    signIn,
    signUp,
  } = useAuth()

  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    otp: '',
    department: 'IT',
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [step, setStep] = useState('email') // 'email' | 'otp'
  const [cooldown, setCooldown] = useState(0)
  const [usePasswordFallback, setUsePasswordFallback] = useState(false)

  const isSignup = mode === 'signup'
  const activeRole = role || ROLES.EMPLOYEE
  const roleDetails = ROLE_NAVIGATION[activeRole] || ROLE_NAVIGATION[ROLES.EMPLOYEE]
  const isDepartmentRole = activeRole === ROLES.AGENT || activeRole === ROLES.MANAGER

  // Cooldown countdown timer
  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setInterval(() => {
      setCooldown((prev) => Math.max(0, prev - 1))
    }, 1000)
    return () => clearInterval(timer)
  }, [cooldown])

  function updateField(event) {
    setError('')
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  }

  // Handle Step 1: Request OTP Code (Login or Registration)
  async function handleRequestCode(event) {
    event?.preventDefault()
    setError('')

    const sanitizedEmail = form.email.trim().toLowerCase()
    if (!sanitizedEmail) {
      setError('Please enter your email address.')
      return
    }

    if (!sanitizedEmail.endsWith('@sailssoftware.com')) {
      setError('Only @sailssoftware.com email addresses are allowed.')
      toast.error('Only @sailssoftware.com company emails are permitted.')
      return
    }

    setLoading(true)

    try {
      if (isSignup) {
        await requestRegistrationOtp({
          name: form.name,
          email: sanitizedEmail,
          role: activeRole,
          department: isDepartmentRole ? form.department : 'IT',
        })
        toast.success('Registration code dispatched. Check your inbox or Gateway terminal.')
      } else {
        await requestOtp(sanitizedEmail)
        toast.success('Verification code dispatched. Check your inbox or Gateway terminal.')
      }
      setStep('otp')
      setCooldown(60)
    } catch (err) {
      setError(err.message)
      toast.error(err.message || 'Failed to request verification code.')
    } finally {
      setLoading(false)
    }
  }

  // Handle Step 2: Verify OTP Code (Login or Registration)
  async function handleVerifyCode(event) {
    event.preventDefault()
    setError('')
    setLoading(true)

    try {
      let user
      if (isSignup) {
        user = await verifyRegistrationOtp(form.email, form.otp, activeRole)
        if (user.role === ROLES.EMPLOYEE && activeRole !== ROLES.EMPLOYEE) {
          toast.info(
            `Account verified! You have Employee access while your request for ${roleDetails.label} (${form.department || 'IT'}) is pending approval by your ${activeRole === ROLES.MANAGER ? 'Admin' : 'Department Manager'}.`,
            { duration: 7000 }
          )
          navigate('/employee', { replace: true })
          return
        }
        toast.success(`Account verified & created! Welcome, ${user.name}!`)
        navigate(roleDetails.homePath, { replace: true })
      } else {
        user = await verifyOtp(form.email, form.otp)
        const targetNav = ROLE_NAVIGATION[user.role] || ROLE_NAVIGATION[ROLES.EMPLOYEE]
        toast.success(`Welcome back to your ${targetNav.label} workspace, ${user.name}!`)
        navigate(targetNav.homePath, { replace: true })
      }
    } catch (err) {
      setError(err.message)
      toast.error(err.message || 'OTP verification failed.')
    } finally {
      setLoading(false)
    }
  }

  // Handle Demo Password Fallback Submit
  function handlePasswordSubmit(event) {
    event.preventDefault()
    setError('')

    try {
      if (isSignup) {
        signUp({ ...form, role: activeRole })
        toast.success('Account created. Welcome to SmartDesk!')
        navigate('/employee', { replace: true })
      } else {
        const loggedInUser = signIn(form.email, form.password)
        const targetNav = ROLE_NAVIGATION[loggedInUser?.role] || roleDetails
        toast.success(`Welcome back to your ${targetNav.label} workspace.`)
        navigate(targetNav.homePath, { replace: true })
      }
    } catch (authError) {
      setError(authError.message)
      if (!isSignup) toast.error('Could not sign in with those details.')
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">
        {step === 'otp'
          ? 'Enter verification code'
          : isSignup
            ? `Sign up as ${roleDetails.label}`
            : 'Sign in to SmartDesk'}
      </h1>
      <p className="mt-1.5 text-sm leading-5 text-neutral-500">
        {step === 'otp'
          ? `We sent a 6-digit verification code to ${form.email}.`
          : isSignup
            ? `Fill in your details below to activate your ${roleDetails.label.toLowerCase()} account.`
            : `Enter your email address to receive a secure login code.`}
      </p>

      {/* --- Case 1: Standard OTP Registration / Login (Step 1: Input details) --- */}
      {!usePasswordFallback && step === 'email' && (
        <form className="mt-5 space-y-4" onSubmit={handleRequestCode}>
          {isSignup && (
            <label className="block space-y-1.5">
              <span className="text-xs font-medium text-neutral-700">Full name</span>
              <span className="relative block">
                <UserRound aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400" size={16} />
                <input
                  autoComplete="name"
                  className="h-11 w-full rounded-lg border border-neutral-200 pl-10 pr-3 text-sm outline-none placeholder:text-neutral-400 focus:border-neutral-400"
                  maxLength={80}
                  name="name"
                  onChange={updateField}
                  placeholder="Your full name"
                  required
                  value={form.name}
                />
              </span>
            </label>
          )}

          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-neutral-700">Work email address</span>
            <span className="relative block">
              <Mail aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400" size={16} />
              <input
                autoComplete="email"
                className="h-11 w-full rounded-lg border border-neutral-200 pl-10 pr-3 text-sm outline-none placeholder:text-neutral-400 focus:border-neutral-400"
                name="email"
                onChange={updateField}
                placeholder="name@sailssoftware.com"
                required
                type="email"
                value={form.email}
              />
            </span>
            <p className="text-[11px] text-neutral-400">Must be an official @sailssoftware.com email address.</p>
          </label>

          {isSignup && isDepartmentRole && (
            <div className="space-y-2">
              <label className="block space-y-1.5">
                <span className="text-xs font-medium text-neutral-700">Department</span>
                <span className="relative block">
                  <Building2 aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400" size={16} />
                  <select
                    className="h-11 w-full rounded-lg border border-neutral-200 bg-white pl-10 pr-3 text-sm text-neutral-800 outline-none focus:border-neutral-400 cursor-pointer"
                    name="department"
                    onChange={updateField}
                    value={form.department}
                  >
                    {DEPARTMENTS.map((dept) => (
                      <option key={dept} value={dept}>{dept}</option>
                    ))}
                  </select>
                </span>
              </label>
              <div className="rounded-lg bg-amber-50 border border-amber-200/80 p-2.5 text-xs text-amber-800 leading-relaxed">
                {activeRole === ROLES.MANAGER
                  ? 'Department Manager accounts require review and approval by the Administrator.'
                  : 'Support Agent accounts require review and approval by your Department Manager.'}
              </div>
            </div>
          )}

          {error && (
            <div aria-live="polite" className="rounded-lg bg-red-50 p-3 text-xs text-red-700 space-y-1.5">
              <p className="font-medium">{error}</p>
              {!isSignup && error.toLowerCase().includes('sign up') && (
                <div className="pt-1">
                  <Link
                    className="inline-flex items-center gap-1 font-semibold text-red-800 underline hover:text-red-950"
                    to="/signup"
                  >
                    Don&apos;t have an account? Sign up here <ArrowRight size={13} />
                  </Link>
                </div>
              )}
            </div>
          )}

          <button
            className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-neutral-900 text-sm font-medium text-white transition hover:bg-neutral-700 disabled:opacity-50"
            disabled={loading || !form.email.trim() || (isSignup && !form.name.trim())}
            type="submit"
          >
            {loading ? <RotateCw className="animate-spin" size={16} /> : <Send size={15} />}
            {isSignup ? 'Send activation code' : 'Send verification code'}
          </button>

          <div className="pt-2 text-center">
            <button
              className="text-xs text-neutral-500 hover:text-neutral-800 underline"
              onClick={() => setUsePasswordFallback(true)}
              type="button"
            >
              {isSignup ? 'Register with password (Demo Mode)' : 'Sign in with password (Demo Mode)'}
            </button>
          </div>
        </form>
      )}

      {/* --- Case 2: Standard OTP Verification (Step 2: Enter 6-digit OTP) --- */}
      {!usePasswordFallback && step === 'otp' && (
        <form className="mt-5 space-y-4" onSubmit={handleVerifyCode}>
          <div className="rounded-xl border border-neutral-200 bg-neutral-50/50 p-3 flex items-center justify-between">
            <div className="text-xs">
              <span className="text-neutral-500">Sending to: </span>
              <span className="font-medium text-neutral-800">{form.email}</span>
            </div>
            <button
              className="inline-flex items-center gap-1 text-xs text-neutral-600 hover:text-neutral-900 font-medium cursor-pointer"
              onClick={() => { setStep('email'); setForm((f) => ({ ...f, otp: '' })); setError(''); }}
              type="button"
            >
              <ArrowLeft size={13} /> Change
            </button>
          </div>

          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-neutral-700">6-Digit Verification Code</span>
            <span className="relative block">
              <KeyRound aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400" size={16} />
              <input
                autoComplete="one-time-code"
                autoFocus
                className="h-11 w-full rounded-lg border border-neutral-200 pl-10 pr-3 text-center text-lg font-mono tracking-widest outline-none placeholder:text-neutral-300 focus:border-neutral-400 disabled:bg-neutral-100 disabled:cursor-not-allowed"
                disabled={error && (error.toLowerCase().includes('locked') || error.toLowerCase().includes('limit exceeded'))}
                maxLength={6}
                name="otp"
                onChange={updateField}
                placeholder="000000"
                required
                type="text"
                value={form.otp}
              />
            </span>
          </label>

          <p className="text-[11px] text-neutral-400 leading-normal">
            For local testing, the OTP code is displayed in the <strong>API Gateway</strong> console log.
          </p>

          {error && (
            <div aria-live="polite" className="rounded-lg bg-red-50 p-3 text-xs text-red-700 space-y-1.5">
              <p>{error}</p>
              {(error.toLowerCase().includes('locked') || error.toLowerCase().includes('limit exceeded')) && (
                <button
                  className="inline-flex items-center gap-1 font-medium text-red-800 underline hover:text-red-950 mt-1 cursor-pointer"
                  onClick={() => { setStep('email'); setForm((f) => ({ ...f, otp: '' })); setError(''); }}
                  type="button"
                >
                  <ArrowLeft size={13} /> Request a new verification code
                </button>
              )}
            </div>
          )}

          <button
            className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-neutral-900 text-sm font-medium text-white transition hover:bg-neutral-700 disabled:opacity-50"
            disabled={loading || form.otp.trim().length !== 6 || (error && (error.toLowerCase().includes('locked') || error.toLowerCase().includes('limit exceeded')))}
            type="submit"
          >
            {loading ? <RotateCw className="animate-spin" size={16} /> : null}
            {isSignup ? 'Verify & Create Account' : 'Verify & Enter Workspace'}
          </button>

          <div className="flex items-center justify-between pt-1">
            <button
              className="text-xs text-neutral-500 hover:text-neutral-800 disabled:opacity-40"
              disabled={cooldown > 0 || loading}
              onClick={handleRequestCode}
              type="button"
            >
              {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
            </button>
            <button
              className="text-xs text-neutral-500 hover:text-neutral-800 underline"
              onClick={() => setUsePasswordFallback(true)}
              type="button"
            >
              Password mode
            </button>
          </div>
        </form>
      )}

      {/* --- Case 3: Demo Password Fallback (Offline Mode) --- */}
      {usePasswordFallback && (
        <form className="mt-5 space-y-4" onSubmit={handlePasswordSubmit}>
          {isSignup && (
            <label className="block space-y-1.5">
              <span className="text-xs font-medium text-neutral-700">Full name</span>
              <span className="relative block">
                <UserRound aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400" size={16} />
                <input autoComplete="name" className="h-11 w-full rounded-lg border border-neutral-200 pl-10 pr-3 text-sm outline-none placeholder:text-neutral-400 focus:border-neutral-400" maxLength={80} name="name" onChange={updateField} placeholder="Your name" required value={form.name} />
              </span>
            </label>
          )}

          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-neutral-700">Email address</span>
            <span className="relative block">
              <Mail aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400" size={16} />
              <input autoComplete="email" className="h-11 w-full rounded-lg border border-neutral-200 pl-10 pr-3 text-sm outline-none placeholder:text-neutral-400 focus:border-neutral-400" name="email" onChange={updateField} placeholder="you@smartdesk.com" required type="email" value={form.email} />
            </span>
          </label>

          <label className="block space-y-1.5">
            <span className="text-xs font-medium text-neutral-700">Password</span>
            <span className="relative block">
              <LockKeyhole aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400" size={16} />
              <input autoComplete={isSignup ? 'new-password' : 'current-password'} className="h-11 w-full rounded-lg border border-neutral-200 pl-10 pr-3 text-sm outline-none placeholder:text-neutral-400 focus:border-neutral-400" minLength={8} name="password" onChange={updateField} placeholder={isSignup ? 'At least 8 characters' : 'Your password'} required type="password" value={form.password} />
            </span>
          </label>

          {error && (
            <div aria-live="polite" className="rounded-lg bg-red-50 p-3 text-xs text-red-700 space-y-1.5">
              <p className="font-medium">{error}</p>
              {!isSignup && error.toLowerCase().includes('sign up') && (
                <div className="pt-1">
                  <Link
                    className="inline-flex items-center gap-1 font-semibold text-red-800 underline hover:text-red-950"
                    to="/signup"
                  >
                    Don&apos;t have an account? Sign up here <ArrowRight size={13} />
                  </Link>
                </div>
              )}
            </div>
          )}

          <button className="inline-flex h-11 w-full items-center justify-center rounded-lg bg-neutral-900 text-sm font-medium text-white transition hover:bg-neutral-700" type="submit">
            {isSignup ? 'Create account (Password)' : 'Sign In with Password'}
          </button>

          <div className="pt-2 text-center">
            <button
              className="text-xs text-neutral-500 hover:text-neutral-800 underline"
              onClick={() => setUsePasswordFallback(false)}
              type="button"
            >
              Switch back to OTP verification
            </button>
          </div>
        </form>
      )}
    </div>
  )
}

export default AuthFormPage
