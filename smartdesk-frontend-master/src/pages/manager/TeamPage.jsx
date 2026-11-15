import { useEffect, useState } from 'react'
import { ArrowRight, CheckCircle2, Clock, UserCheck, UserX, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { ROLES } from '../../context/roles.js'
import { useTickets } from '../../features/tickets/useTickets.js'
import { useAuth } from '../../features/auth/useAuth.js'
import {
  fetchPendingAccessRequests,
  fetchActiveDepartmentStaff,
  approveAccessRequest,
  rejectAccessRequest,
  updateStaffDepartment,
} from '../../features/auth/accessRequestService.js'

function TeamPage() {
  const { tickets } = useTickets()
  const { user } = useAuth()
  const isAdmin = user?.role === ROLES.ADMIN

  const [pendingRequests, setPendingRequests] = useState([])
  const [loadingRequests, setLoadingRequests] = useState(true)
  const [staff, setStaff] = useState([])
  const [loadingStaff, setLoadingStaff] = useState(true)
  const [actionInProgress, setActionInProgress] = useState(null)
  const [updatingDeptUserId, setUpdatingDeptUserId] = useState(null)

  useEffect(() => {
    loadRequests()
    loadStaff()
  }, [])

  async function loadRequests() {
    setLoadingRequests(true)
    try {
      const list = await fetchPendingAccessRequests()
      setPendingRequests(list)
    } catch {
      // Backend request fallback
    } finally {
      setLoadingRequests(false)
    }
  }

  async function loadStaff() {
    setLoadingStaff(true)
    try {
      const list = await fetchActiveDepartmentStaff()
      setStaff(list)
    } catch {
      // Backend request fallback
    } finally {
      setLoadingStaff(false)
    }
  }

  async function handleApprove(request) {
    setActionInProgress(request.id)
    try {
      await approveAccessRequest(request.id)
      const roleLabel = request.requestedRole?.replace('ROLE_', '') || 'Agent'
      toast.success(`Access granted! ${request.userName} is now an active ${roleLabel} for ${request.department || 'the team'}.`)
      setPendingRequests((prev) => prev.filter((r) => r.id !== request.id))
      // Dynamically reload staff list immediately so the new agent/manager appears in real-time
      await loadStaff()
    } catch (err) {
      toast.error(err.response?.data?.error || err.message || 'Failed to approve request')
    } finally {
      setActionInProgress(null)
    }
  }

  async function handleReject(request) {
    setActionInProgress(request.id)
    try {
      await rejectAccessRequest(request.id, 'Declined by reviewer')
      toast.info(`Request for ${request.userName} has been rejected.`)
      setPendingRequests((prev) => prev.filter((r) => r.id !== request.id))
    } catch (err) {
      toast.error(err.response?.data?.error || err.message || 'Failed to reject request')
    } finally {
      setActionInProgress(null)
    }
  }

  async function handleDepartmentChange(member, newDepartment) {
    if (!newDepartment || newDepartment === member.department) return
    setUpdatingDeptUserId(member.id)
    try {
      await updateStaffDepartment(member.id, newDepartment)
      const roleLabel = (member.role === 'ROLE_MANAGER' || member.role === ROLES.MANAGER) ? 'Manager' : 'Support Agent'
      toast.success(`Reassigned ${member.name} (${roleLabel}) to ${newDepartment} department.`)
      setStaff((prev) =>
        prev.map((s) => (s.id === member.id ? { ...s, department: newDepartment } : s))
      )
    } catch (err) {
      toast.error(err.response?.data?.error || err.message || 'Failed to update department')
    } finally {
      setUpdatingDeptUserId(null)
    }
  }

  const team = staff.map((member) => ({
    ...member,
    activeTickets: tickets.filter((ticket) =>
      (ticket.assignee === member.name ||
        ticket.assigneeId === member.id ||
        ticket.assigneeEmail === member.email) &&
      ticket.status !== 'Closed'
    ).length,
  }))

  return (
    <section className="mx-auto w-full max-w-5xl px-6 py-9 sm:px-10 sm:py-11">
      <p className="text-sm font-medium text-neutral-500">
        {isAdmin ? 'Admin workspace' : `${user?.department || 'Department'} manager workspace`}
      </p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-neutral-900">Team & Access Management</h1>
      <p className="mt-2 text-sm text-neutral-500">
        {isAdmin
          ? 'Approve manager role promotions and review department staff workloads.'
          : `Review and approve incoming Support Agent requests for the ${user?.department || ''} department.`}
      </p>

      {/* KPI Cards */}
      <div className="mt-7 grid gap-3 sm:grid-cols-3">
        <div className="rounded-2xl border border-neutral-200 bg-white p-5">
          <p className="text-sm text-neutral-500">Pending Role Requests</p>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-3xl font-semibold tracking-tight text-neutral-900">
              {pendingRequests.length}
            </span>
            {pendingRequests.length > 0 && (
              <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">
                Action needed
              </span>
            )}
          </div>
        </div>
        <div className="rounded-2xl border border-neutral-200 bg-white p-5">
          <p className="text-sm text-neutral-500">Active Department Staff</p>
          <p className="mt-4 text-3xl font-semibold tracking-tight text-neutral-900">{team.length}</p>
        </div>
        <div className="rounded-2xl border border-neutral-200 bg-white p-5">
          <p className="text-sm text-neutral-500">Unassigned Active Tickets</p>
          <p className="mt-4 text-3xl font-semibold tracking-tight text-neutral-900">
            {tickets.filter((ticket) => (isAdmin || ticket.department === user?.department) && !ticket.assignee && ticket.status !== 'Closed').length}
          </p>
        </div>
      </div>

      {/* Section 1: Pending Role Approval Requests */}
      <section className="mt-8 overflow-hidden rounded-2xl border border-amber-200/80 bg-white shadow-xs">
        <div className="flex items-center justify-between border-b border-neutral-100 bg-amber-50/50 px-5 py-4 sm:px-6">
          <div className="flex items-center gap-2">
            <Clock className="size-4 text-amber-600" />
            <h2 className="text-sm font-semibold text-neutral-900">
              {isAdmin ? 'Pending Manager & Staff Access Requests' : `Pending Agent Access Requests (${user?.department || 'Department'})`}
            </h2>
          </div>
          <span className="rounded-full bg-white px-2.5 py-0.5 text-xs font-semibold text-neutral-700 border border-neutral-200">
            {pendingRequests.length} pending
          </span>
        </div>

        {loadingRequests ? (
          <div className="p-8 text-center text-sm text-neutral-500">Checking pending requests...</div>
        ) : pendingRequests.length === 0 ? (
          <div className="p-8 text-center">
            <CheckCircle2 className="mx-auto size-8 text-neutral-300" />
            <p className="mt-2 text-sm font-medium text-neutral-700">All caught up</p>
            <p className="mt-1 text-xs text-neutral-500">
              No pending role requests for your {isAdmin ? 'organization' : `${user?.department} department`}.
            </p>
          </div>
        ) : (
          <div className="divide-y divide-neutral-100">
            {pendingRequests.map((req) => {
              const roleDisplay = req.requestedRole === 'ROLE_MANAGER' ? 'Manager' : 'Support Agent'
              const isWorking = actionInProgress === req.id

              return (
                <div className="flex flex-col gap-4 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6" key={req.id}>
                  <div className="flex items-center gap-3">
                    <span className="grid size-10 place-items-center rounded-full bg-amber-100 text-sm font-semibold text-amber-800">
                      {req.userName?.charAt(0) || 'U'}
                    </span>
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-medium text-neutral-900">{req.userName}</p>
                        <span className="inline-flex items-center rounded-md bg-neutral-100 px-2 py-0.5 text-xs font-medium text-neutral-700">
                          Current: {req.currentRole === 'ROLE_EMPLOYEE' ? 'Employee' : req.currentRole}
                        </span>
                      </div>
                      <p className="mt-0.5 text-xs text-neutral-500">
                        {req.userEmail} · Requests <strong className="font-semibold text-neutral-800">{roleDisplay}</strong> for <span className="rounded bg-neutral-100 px-1.5 py-0.5 text-neutral-700 font-medium">{req.department || 'IT'}</span>
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 sm:justify-end">
                    <button
                      type="button"
                      disabled={isWorking}
                      onClick={() => handleApprove(req)}
                      className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50"
                    >
                      <UserCheck size={14} />
                      Approve as {roleDisplay}
                    </button>
                    <button
                      type="button"
                      disabled={isWorking}
                      onClick={() => handleReject(req)}
                      className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-neutral-200 bg-white px-3 py-1.5 text-xs font-medium text-neutral-700 hover:bg-neutral-50 disabled:opacity-50"
                    >
                      <UserX size={14} />
                      Reject
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </section>

      {/* Section 2: Active Team Members */}
      <section className="mt-8 overflow-hidden rounded-2xl border border-neutral-200 bg-white shadow-xs">
        <div className="flex items-center justify-between border-b border-neutral-100 px-5 py-4 sm:px-6">
          <h2 className="text-sm font-semibold text-neutral-900">
            {isAdmin ? 'Active Department Staff & Managers' : `Active Department Staff (${user?.department || 'Department'})`}
          </h2>
          <span className="rounded-full bg-neutral-100 px-2.5 py-0.5 text-xs font-semibold text-neutral-700">
            {team.length} active
          </span>
        </div>

        {loadingStaff ? (
          <div className="p-8 text-center text-sm text-neutral-500">Loading department staff...</div>
        ) : team.length === 0 ? (
          <div className="p-8 text-center">
            <Users className="mx-auto size-8 text-neutral-300" />
            <p className="mt-2 text-sm font-medium text-neutral-700">No active staff members yet</p>
            <p className="mt-1 text-xs text-neutral-500">
              {isAdmin
                ? 'Approved managers and department agents will automatically appear here.'
                : `Approved ${user?.department || ''} agents will appear here.`}
            </p>
          </div>
        ) : (
          <div className="divide-y divide-neutral-100">
            {team.map((member) => {
              const initials = (member.name || member.email || 'U')
                .split(' ')
                .filter(Boolean)
                .map((part) => part[0])
                .join('')
                .slice(0, 2)
                .toUpperCase()
              const isManager = member.role === 'ROLE_MANAGER' || member.role === ROLES.MANAGER
              const roleLabel = isManager ? 'Manager' : 'Support Agent'

              return (
                <div className="flex flex-col gap-4 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6" key={member.id || member.email}>
                  <div className="flex items-center gap-3">
                    <span className="grid size-10 place-items-center rounded-full bg-neutral-100 text-sm font-semibold text-neutral-700">
                      {initials}
                    </span>
                    <div>
                      <p className="text-sm font-medium text-neutral-900">{member.name}</p>
                      <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs">
                        <span className="font-semibold text-neutral-800">{roleLabel}</span>
                        {isAdmin ? (
                          <div className="inline-flex items-center gap-1">
                            <span className="text-neutral-400">· Dept:</span>
                            <select
                              aria-label={`Change department for ${member.name}`}
                              className="h-6 rounded-md border border-neutral-300 bg-white px-1.5 py-0 text-xs font-medium text-neutral-800 shadow-2xs hover:border-neutral-400 focus:border-neutral-900 focus:outline-none cursor-pointer disabled:opacity-50"
                              disabled={updatingDeptUserId === member.id}
                              onChange={(e) => handleDepartmentChange(member, e.target.value)}
                              value={member.department || 'IT'}
                            >
                              {['IT', 'HR', 'Facilities', 'Engineering', 'Finance', 'Operations'].map((dept) => (
                                <option key={dept} value={dept}>
                                  {dept}
                                </option>
                              ))}
                            </select>
                            {updatingDeptUserId === member.id && (
                              <span className="text-[10px] text-neutral-400 animate-pulse">Saving...</span>
                            )}
                          </div>
                        ) : (
                          <span className="text-neutral-500">· {member.department || 'IT'}</span>
                        )}
                        <span className="text-neutral-400 font-mono text-[11px]">({member.email})</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center justify-between gap-4 sm:justify-end">
                    <div className="text-sm text-neutral-600">{member.activeTickets} active assigned</div>
                    <Link className="inline-flex items-center gap-1 text-xs font-medium text-neutral-600 hover:text-neutral-900" to={isAdmin ? '/admin/tickets' : '/manager/tickets'}>
                      View tickets <ArrowRight size={14} />
                    </Link>
                  </div>
                </div>
              )
            })}
          </div>
        )}
        <div className="flex items-start gap-3 border-t border-neutral-100 bg-neutral-50 px-5 py-4 sm:px-6">
          <Users aria-hidden="true" className="mt-0.5 text-neutral-500" size={16} />
          <p className="text-xs leading-5 text-neutral-500">
            {isAdmin
              ? 'Admins have full visibility across all department managers and agents.'
              : `Staff shown here belong to the ${user?.department} department.`}
          </p>
        </div>
      </section>
    </section>
  )
}

export default TeamPage
