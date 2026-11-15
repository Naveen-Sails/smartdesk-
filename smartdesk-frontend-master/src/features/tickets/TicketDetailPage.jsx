import { useState, useEffect } from 'react'
import { AlertCircle, ArrowLeft, CheckCircle2, RotateCcw, Send, UserRound } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { ROLES } from '../../context/roles.js'
import { useAuth } from '../auth/useAuth.js'
import { useTickets } from './useTickets.js'
import { isUserTicket } from './ticketStore.js'
import { AGENT_TICKET_STATUSES } from './demoTickets.js'
import TicketStatusBadge from './TicketStatusBadge.jsx'
import PriorityBadge from './PriorityBadge.jsx'
import { normalizeBackendTicket } from './TicketContext.jsx'
import api from '../../services/api.js'

function formatDateTime(value) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function TicketDetailPage() {
  const { ticketId } = useParams()
  const { pathname } = useLocation()
  const { user } = useAuth()
  const role = user.role
  const { tickets, updateTicket, addComment } = useTickets()

  const [fetchedTicket, setFetchedTicket] = useState(null)
  const [loading, setLoading] = useState(false)

  const ticketFromContext = tickets.find((item) => item.id === ticketId || String(item.backendId) === ticketId || item.id === ('SD-' + ticketId))

  useEffect(() => {
    if (ticketFromContext || !ticketId || ticketId === 'undefined') return
    const rawId = ticketId.startsWith('SD-') ? ticketId.replace('SD-', '') : ticketId
    setLoading(true)
    api.get(`/v1/tickets/${rawId}`)
      .then((res) => {
        if (res.data) {
          setFetchedTicket(normalizeBackendTicket(res.data, user))
        }
      })
      .catch((err) => {
        console.debug('Failed to fetch ticket directly:', err.message)
      })
      .finally(() => setLoading(false))
  }, [ticketFromContext, ticketId, user])

  const ticket = ticketFromContext || fetchedTicket

  const isRequester = isUserTicket(ticket, user)
  const isEmployeeMatch = role === ROLES.EMPLOYEE && isRequester
  const isManagerMatch = role === ROLES.MANAGER && (
    !user.department || ticket?.department === user.department
  )
  const isAgentMatch = role === ROLES.AGENT && (
    !ticket?.department || !user.department || ticket.department === user.department || ticket.department === 'UNCLASSIFIED'
  ) && (!ticket?.assignee || ticket.assignee === user.name)

  const canViewTicket = ticket && (
    role === ROLES.ADMIN || isRequester || isManagerMatch || isAgentMatch || isEmployeeMatch
  )
  const [comment, setComment] = useState('')

  const userRoleBase = role === ROLES.ADMIN ? 'admin' : role === ROLES.MANAGER ? 'manager' : role === ROLES.AGENT ? 'agent' : 'employee'
  const backPath = isRequester && (pathname.includes('/my-requests') || (!isManagerMatch && !isAgentMatch))
    ? `/${userRoleBase}/my-requests`
    : role === ROLES.EMPLOYEE
      ? '/employee/tickets'
      : role === ROLES.MANAGER
        ? '/manager/tickets'
        : role === ROLES.ADMIN
          ? '/admin/tickets'
        : pathname.includes('/queue/') ? '/agent/queue' : '/agent/tickets'

  if (loading) {
    return (
      <section className="mx-auto max-w-3xl px-6 py-12 sm:px-10">
        <Link className="inline-flex items-center gap-2 text-sm text-neutral-500 hover:text-neutral-900" to={backPath}>
          <ArrowLeft size={16} /> Back to tickets
        </Link>
        <p className="mt-8 text-sm text-neutral-500">Loading ticket details...</p>
      </section>
    )
  }

  if (!canViewTicket) {
    return (
      <section className="mx-auto max-w-3xl px-6 py-12 sm:px-10">
        <Link className="inline-flex items-center gap-2 text-sm text-neutral-500 hover:text-neutral-900" to={backPath}>
          <ArrowLeft size={16} /> Back to tickets
        </Link>
        <h1 className="mt-8 text-2xl font-semibold text-neutral-900">Ticket not found</h1>
        <p className="mt-2 text-sm text-neutral-500">This ticket is not available to your account.</p>
      </section>
    )
  }

  const canClaim = role === ROLES.AGENT && !ticket.assignee && ticket.status !== 'Closed'
  const canUpdate = role === ROLES.AGENT && ticket.assignee === user.name && ticket.status !== 'Closed'
  const canComment = ticket.status !== 'Closed' && (canUpdate || role === ROLES.EMPLOYEE || isRequester)

  const [showResolveConfirmModal, setShowResolveConfirmModal] = useState(false)
  const [showReopenBox, setShowReopenBox] = useState(false)
  const [reopenReason, setReopenReason] = useState('')

  function handleComment(event) {
    event.preventDefault()
    if (!comment.trim()) return
    addComment(ticket.id, comment.trim(), user.name)
    setComment('')
  }

  function handleStatusChange(event) {
    const nextStatus = event.target.value
    if (nextStatus === ticket.status) return
    if (nextStatus === 'Resolved') {
      setShowResolveConfirmModal(true)
      return
    }
    updateTicket(ticket.id, { status: nextStatus })
    toast.success(`Ticket status changed to ${nextStatus}.`)
  }

  function confirmMarkResolved() {
    updateTicket(ticket.id, { status: 'Resolved' })
    toast.success('Marked as Resolved. Requester has been notified to verify and acknowledge resolution.')
    setShowResolveConfirmModal(false)
  }

  function handleAssign() {
    if (ticket.assignee !== user.name) {
      updateTicket(ticket.id, { assignee: user.name })
      toast.success('Ticket assigned to you.')
    }
  }

  function handleAcknowledgeResolution() {
    updateTicket(ticket.id, { status: 'Closed', acknowledgedBy: user.name })
    toast.success('Resolution confirmed. Ticket is now Closed.')
  }

  function handleReopenTicket(event) {
    event.preventDefault()
    if (!reopenReason.trim()) return
    updateTicket(ticket.id, {
      status: 'In Progress',
      reopenedBy: user.name,
      reopenReason: reopenReason.trim(),
    })
    addComment(ticket.id, `Requester reopened ticket: ${reopenReason.trim()}`, user.name)
    toast.success('Ticket reopened and returned to In Progress.')
    setShowReopenBox(false)
    setReopenReason('')
  }

  return (
    <section className="mx-auto w-full max-w-6xl px-6 py-8 sm:px-10 sm:py-10">
      <Link className="inline-flex items-center gap-2 text-sm text-neutral-500 hover:text-neutral-900" to={backPath}>
        <ArrowLeft size={16} /> Back to {role === ROLES.AGENT ? 'queue' : 'tickets'}
      </Link>

      <div className="mt-6 flex flex-col justify-between gap-4 border-b border-neutral-200 pb-6 sm:flex-row sm:items-start">
        <div>
          <p className="text-xs font-medium text-neutral-500">{ticket.id} · {ticket.department} · {ticket.category}</p>
          <h1 className="mt-2 max-w-3xl text-2xl font-semibold tracking-tight text-neutral-900 sm:text-3xl">{ticket.title}</h1>
          <p className="mt-2 text-sm text-neutral-500">Created {formatDateTime(ticket.createdAt)}</p>
        </div>
        <TicketStatusBadge status={ticket.status} />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_280px]">
        <div className="space-y-5">
          <article className="rounded-2xl border border-neutral-200 bg-white p-5 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-sm font-semibold text-neutral-900">AI triage summary</h2>
              <span className="text-xs font-medium text-neutral-500">{ticket.department} · {ticket.category}</span>
            </div>
            <p className="mt-3 text-sm leading-6 text-neutral-700">{ticket.summary}</p>
            <h2 className="mt-5 border-t border-neutral-100 pt-4 text-sm font-semibold text-neutral-900">Description</h2>
            <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-neutral-700">{ticket.description}</p>
          </article>

          <section className="rounded-2xl border border-neutral-200 bg-white">
            <div className="border-b border-neutral-100 px-5 py-4 sm:px-6">
              <h2 className="text-sm font-semibold text-neutral-900">Activity</h2>
            </div>
            <div className="divide-y divide-neutral-100">
              {ticket.activity.map((item, index) => (
                <article className="flex gap-3 px-5 py-4 sm:px-6" key={`${item.createdAt}-${index}`}>
                  <span className="grid size-8 shrink-0 place-items-center rounded-full bg-neutral-100 text-xs font-semibold text-neutral-600">
                    {item.author.slice(0, 1)}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-col justify-between gap-1 sm:flex-row sm:items-center">
                      <p className="text-sm font-medium text-neutral-800">{item.author}</p>
                      <time className="text-xs text-neutral-400">{formatDateTime(item.createdAt)}</time>
                    </div>
                    <p className="mt-1 whitespace-pre-wrap text-sm leading-5 text-neutral-600">{item.message}</p>
                  </div>
                </article>
              ))}
            </div>
            {canComment && (
              <form className="border-t border-neutral-100 p-4 sm:p-5" onSubmit={handleComment}>
                <label className="sr-only" htmlFor="ticket-comment">Add a comment</label>
                <textarea
                  className="min-h-24 w-full resize-y rounded-xl border border-neutral-200 p-3 text-sm outline-none placeholder:text-neutral-400 focus:border-neutral-400"
                  id="ticket-comment"
                  onChange={(event) => setComment(event.target.value)}
                  placeholder="Write a reply..."
                  value={comment}
                />
                <div className="mt-3 flex items-center justify-between gap-3">
                  <button className="inline-flex h-9 items-center gap-2 rounded-lg bg-neutral-900 px-3 text-xs font-medium text-white hover:bg-neutral-700 disabled:cursor-not-allowed disabled:opacity-50" disabled={!comment.trim()} type="submit">
                    <Send size={14} /> Add reply
                  </button>
                </div>
              </form>
            )}
          </section>
        </div>

        <aside className="space-y-4">
          <section className="rounded-2xl border border-neutral-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-neutral-900">Request details</h2>
            <dl className="mt-4 space-y-4 text-sm">
              <div>
                <dt className="text-xs text-neutral-500">Requester</dt>
                <dd className="mt-1 font-medium text-neutral-800">{ticket.requester}</dd>
                <dd className="text-xs text-neutral-500">{ticket.requesterEmail}</dd>
              </div>
              <div>
                <dt className="text-xs text-neutral-500">Priority</dt>
                <dd className="mt-1"><PriorityBadge priority={ticket.priority} /></dd>
              </div>
              <div>
                <dt className="text-xs text-neutral-500">Assignee</dt>
                <dd className="mt-1 flex items-center gap-1.5 font-medium text-neutral-800">
                  <UserRound size={14} /> {ticket.assignee || 'Unassigned'}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-neutral-500">Last updated</dt>
                <dd className="mt-1 font-medium text-neutral-800">{formatDateTime(ticket.updatedAt)}</dd>
              </div>
            </dl>
          </section>

          {(canClaim || canUpdate) && (
            <section className="rounded-2xl border border-neutral-200 bg-white p-5">
              <h2 className="text-sm font-semibold text-neutral-900">Agent actions</h2>
              {canClaim && (
                <button className="mt-4 h-9 w-full rounded-lg border border-neutral-200 text-sm font-medium text-neutral-700 hover:bg-neutral-50" onClick={handleAssign} type="button">
                  Assign to me
                </button>
              )}
              {canUpdate && (
                <label className="mt-4 block space-y-2">
                  <span className="text-xs text-neutral-500">Update status</span>
                  <select className="h-10 w-full rounded-lg border border-neutral-200 bg-white px-3 text-sm outline-none focus:border-neutral-400" onChange={handleStatusChange} value={ticket.status}>
                    {AGENT_TICKET_STATUSES.map((status) => <option key={status}>{status}</option>)}
                  </select>
                </label>
              )}
            </section>
          )}

          {(isRequester || role === ROLES.EMPLOYEE) && ticket.status === 'Resolved' && (
            <section className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
              <div className="flex items-center gap-2 text-emerald-900">
                <CheckCircle2 size={16} />
                <h2 className="text-sm font-semibold">Resolution Acknowledgement</h2>
              </div>
              <p className="mt-1.5 text-xs leading-5 text-emerald-800">
                Support has marked this request as resolved. Has your issue been resolved to your satisfaction?
              </p>
              <div className="mt-4 space-y-2">
                <button
                  className="flex h-9 w-full items-center justify-center gap-1.5 rounded-lg bg-emerald-700 px-3 text-xs font-medium text-white hover:bg-emerald-800 transition"
                  onClick={handleAcknowledgeResolution}
                  type="button"
                >
                  <CheckCircle2 size={14} /> Yes, Confirm & Close
                </button>
                {!showReopenBox ? (
                  <button
                    className="flex h-9 w-full items-center justify-center gap-1.5 rounded-lg border border-emerald-300 bg-white px-3 text-xs font-medium text-emerald-800 hover:bg-emerald-100/60 transition"
                    onClick={() => setShowReopenBox(true)}
                    type="button"
                  >
                    <RotateCcw size={13} /> No, Issue Still Persists (Reopen)
                  </button>
                ) : (
                  <div className="mt-2 space-y-2 border-t border-emerald-200 pt-2.5">
                    <label className="block text-xs font-medium text-emerald-900" htmlFor="reopen-reason">
                      Why does this still need attention?
                    </label>
                    <textarea
                      className="w-full rounded-lg border border-emerald-300 bg-white p-2 text-xs outline-none focus:border-emerald-500"
                      id="reopen-reason"
                      onChange={(e) => setReopenReason(e.target.value)}
                      placeholder="Describe what still needs attention..."
                      rows={2}
                      value={reopenReason}
                    />
                    <div className="flex gap-2">
                      <button
                        className="flex-1 rounded-lg bg-red-600 py-1.5 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-50 transition"
                        disabled={!reopenReason.trim()}
                        onClick={handleReopenTicket}
                        type="button"
                      >
                        Reopen Ticket
                      </button>
                      <button
                        className="rounded-lg border border-neutral-300 bg-white px-3 py-1.5 text-xs text-neutral-600 hover:bg-neutral-50 transition"
                        onClick={() => setShowReopenBox(false)}
                        type="button"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </section>
          )}

          {(role === ROLES.MANAGER || role === ROLES.ADMIN) && !isRequester && (
            <div className="rounded-xl bg-[#f7f7f8] p-4 text-xs leading-5 text-neutral-600">
              Manager preview is read-only.
            </div>
          )}
        </aside>
      </div>

      {showResolveConfirmModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl border border-neutral-200">
            <div className="flex items-center gap-3 text-neutral-900">
              <div className="grid size-10 place-items-center rounded-full bg-emerald-100 text-emerald-700">
                <CheckCircle2 size={20} />
              </div>
              <div>
                <h3 className="text-base font-semibold">Mark Request as Resolved?</h3>
                <p className="text-xs text-neutral-500">Requires Requester Acknowledgement</p>
              </div>
            </div>
            <p className="mt-3 text-sm text-neutral-600 leading-relaxed">
              Moving this request to <strong>Resolved</strong> notifies the user to verify whether their issue has been resolved. The ticket will remain pending their confirmation before officially closing.
            </p>
            <div className="mt-5 flex items-center justify-end gap-2.5">
              <button
                className="rounded-lg border border-neutral-200 px-3.5 py-2 text-xs font-medium text-neutral-700 hover:bg-neutral-50 transition"
                onClick={() => setShowResolveConfirmModal(false)}
                type="button"
              >
                Cancel
              </button>
              <button
                className="rounded-lg bg-emerald-700 px-4 py-2 text-xs font-medium text-white hover:bg-emerald-800 transition"
                onClick={confirmMarkResolved}
                type="button"
              >
                Yes, mark resolved & request acknowledgement
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

export default TicketDetailPage
