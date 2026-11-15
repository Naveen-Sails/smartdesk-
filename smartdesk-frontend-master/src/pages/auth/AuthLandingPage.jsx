import { useState } from 'react'
import { BriefcaseBusiness, ChartNoAxesCombined, Headset } from 'lucide-react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../../features/auth/useAuth.js'
import AuthFormPage from './AuthFormPage.jsx'
import { ROLES } from '../../context/roles.js'
import { ROLE_NAVIGATION } from '../../routes/navigation.js'

const SIGNUP_ROLES = [
  { role: ROLES.EMPLOYEE, label: 'Employee', icon: BriefcaseBusiness },
  { role: ROLES.AGENT, label: 'Support Agent', icon: Headset },
  { role: ROLES.MANAGER, label: 'Manager', icon: ChartNoAxesCombined },
]
const VALID_SIGNUP_ROLES = SIGNUP_ROLES.map((r) => r.role)

function AuthLandingPage({ initialMode = 'login' }) {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { role: routeRole } = useParams()
  const { user } = useAuth()

  const isSignupPath = pathname.startsWith('/signup')
  const mode = isSignupPath ? 'signup' : initialMode

  const initialSignupRole = VALID_SIGNUP_ROLES.includes(routeRole) ? routeRole : ROLES.EMPLOYEE
  const [signupRole, setSignupRole] = useState(initialSignupRole)

  if (user) return <Navigate replace to={ROLE_NAVIGATION[user.role]?.homePath || '/employee'} />

  function selectMode(nextMode) {
    if (nextMode === 'signup') {
      navigate('/signup', { replace: true })
    } else {
      navigate('/login', { replace: true })
    }
  }

  return (
    <main className="grid min-h-svh place-items-center bg-[#f7f7f8] px-4 py-8 sm:px-8">
      <div className="w-full max-w-lg">
        <header className="mb-6 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <span className="grid size-9 place-items-center rounded-xl bg-neutral-900 text-white">
              <Headset size={18} />
            </span>
            <span className="text-base font-semibold tracking-tight text-neutral-900">SmartDesk</span>
          </div>
        </header>

        <section className="rounded-2xl border border-neutral-200 bg-white p-5 shadow-sm sm:p-7">
          <div className="border-b border-neutral-200 grid grid-cols-2" role="tablist">
            {[
              { value: 'login', label: 'Sign In' },
              { value: 'signup', label: 'Sign Up' },
            ].map((option) => (
              <button
                aria-selected={mode === option.value}
                className={`border-b-2 pb-3 pt-1 text-sm font-medium transition ${
                  mode === option.value
                    ? 'border-neutral-900 text-neutral-900 font-semibold'
                    : 'border-transparent text-neutral-500 hover:text-neutral-800'
                }`}
                key={option.value}
                onClick={() => selectMode(option.value)}
                role="tab"
                type="button"
              >
                {option.label}
              </button>
            ))}
          </div>

          {mode === 'signup' && (
            <div className="mt-5">
              <p className="text-xs font-semibold text-neutral-700">Choose your account role</p>
              <div
                aria-label="Choose account role"
                className="mt-2 grid grid-cols-3 gap-1.5 rounded-xl bg-neutral-100 p-1"
                role="tablist"
              >
                {SIGNUP_ROLES.map(({ role: optionRole, label, icon: Icon }) => (
                  <button
                    aria-selected={signupRole === optionRole}
                    className={`flex min-h-11 flex-col items-center justify-center gap-1 rounded-lg px-2 py-1.5 text-[11px] font-medium transition sm:flex-row sm:gap-1.5 sm:text-xs ${
                      signupRole === optionRole
                        ? 'bg-white text-neutral-900 shadow-2xs font-semibold'
                        : 'text-neutral-500 hover:text-neutral-800'
                    }`}
                    key={optionRole}
                    onClick={() => setSignupRole(optionRole)}
                    role="tab"
                    type="button"
                  >
                    <Icon aria-hidden="true" size={15} />
                    <span>{label}</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="pt-5">
            <AuthFormPage
              key={`${mode}-${mode === 'signup' ? signupRole : 'universal'}`}
              mode={mode}
              role={mode === 'signup' ? signupRole : undefined}
            />
          </div>
        </section>
      </div>
    </main>
  )
}

export default AuthLandingPage
