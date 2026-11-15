import { useEffect, useState } from 'react'
import { TicketContext, TICKET_UPDATED_EVENT } from './ticketStore.js'
import { DEMO_TICKETS } from './demoTickets.js'
import { classifyTicket } from './classifyTicket.js'
import { useAuth } from '../auth/useAuth.js'
import { addMockNotification } from '../notifications/notificationStore.js'
import { TICKET_STATUSES, TICKET_PRIORITIES } from './demoTickets.js'
import api from '../../services/api.js'

const TICKETS_STORAGE_KEY = 'smartdesk-demo-tickets'

const STATUS_MAP = {
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}

const PRIORITY_MAP = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
}

export function normalizeBackendTicket(t, currentUser) {
  const displayStatus = STATUS_MAP[t.status] || t.status || 'Open'
  const displayPriority = PRIORITY_MAP[t.priority] || t.priority || 'Medium'
  const displayDepartment = t.department || t.category || 'IT'
  const idStr = typeof t.id === 'number' ? `SD-${t.id}` : (t.id || 'SD-1')

  const isCurrentUserCreator = Boolean(
    (t.requesterId && currentUser?.id && String(t.requesterId).toLowerCase() === String(currentUser.id).toLowerCase()) ||
    (t.requesterEmail && currentUser?.email && t.requesterEmail.trim().toLowerCase() === currentUser.email.trim().toLowerCase()) ||
    (t.requesterName && currentUser?.name && t.requesterName.trim().toLowerCase() === currentUser.name.trim().toLowerCase()) ||
    (t.requester && currentUser?.name && t.requester.trim().toLowerCase() === currentUser.name.trim().toLowerCase())
  )

  const reqEmail = t.requesterEmail || (isCurrentUserCreator ? currentUser?.email : '') || ''
  const reqName = t.requesterName || t.requester || (isCurrentUserCreator ? currentUser?.name : 'Employee')

  return {
    ...t,
    id: idStr,
    backendId: t.id,
    title: t.title,
    description: t.description,
    status: displayStatus,
    priority: displayPriority,
    department: displayDepartment,
    category: t.category || displayDepartment,
    summary: t.summary || '',
    requester: reqName,
    requesterEmail: reqEmail,
    requesterId: t.requesterId || (isCurrentUserCreator ? currentUser?.id : null),
    assignee: t.assigneeName || (t.assigneeId ? 'Assigned Agent' : null),
    createdAt: t.createdAt,
    updatedAt: t.resolvedAt || t.createdAt,
    activity: [
      {
        author: reqName,
        message: t.summary ? `AI Triage: ${t.summary}` : 'Submitted this request for triage.',
        createdAt: t.createdAt,
        kind: 'created',
      },
    ],
  }
}

function toBackendStatus(status) {
  const map = {
    Open: 'OPEN',
    'In Progress': 'IN_PROGRESS',
    Resolved: 'RESOLVED',
    Closed: 'CLOSED',
    'Waiting on employee': 'IN_PROGRESS',
  }
  return map[status] || (typeof status === 'string' ? status.toUpperCase() : 'OPEN')
}

function loadDemoTickets() {
  try {
    const storedTickets = window.localStorage.getItem(TICKETS_STORAGE_KEY)
    const parsedTickets = storedTickets ? JSON.parse(storedTickets) : null
    if (Array.isArray(parsedTickets)) {
      // Purge any legacy dummy tickets from storage
      return parsedTickets.filter(
        (t) => t.requesterEmail !== 'employee@smartdesk.com' && !String(t.id).startsWith('SD-104')
      )
    }
    return []
  } catch {
    return []
  }
}

function nextTicketId(tickets) {
  const highestNumber = tickets.reduce((highest, ticket) => {
    const value = Number(ticket.id.replace('SD-', ''))
    return Number.isNaN(value) ? highest : Math.max(highest, value)
  }, 1042)

  return `SD-${highestNumber + 1}`
}

export function TicketProvider({ children }) {
  const { user } = useAuth()
  const [tickets, setTickets] = useState(loadDemoTickets)
  const [backendConnected, setBackendConnected] = useState(false)

  // Fetch tickets from Backend via Gateway if user is authenticated
  useEffect(() => {
    if (!user) return

    let cancelled = false
    async function fetchBackendTickets() {
      try {
        const response = await api.get('/v1/tickets')
        const rawList = Array.isArray(response.data)
          ? response.data
          : (Array.isArray(response.data?.content) ? response.data.content : [])

        let combinedRaw = [...rawList]

        // For non-employees (Manager, Agent, Admin), also fetch personal tickets (mine=true)
        // so that any tickets they personally raised (even cross-department) are included
        if (user.role && user.role !== 'ROLE_EMPLOYEE' && user.role !== 'employee') {
          try {
            const mineResponse = await api.get('/v1/tickets?mine=true')
            const mineList = Array.isArray(mineResponse.data)
              ? mineResponse.data
              : (Array.isArray(mineResponse.data?.content) ? mineResponse.data.content : [])

            if (Array.isArray(mineList)) {
              const existingIds = new Set(combinedRaw.map((t) => t.id))
              for (const item of mineList) {
                if (!existingIds.has(item.id)) {
                  combinedRaw.push(item)
                  existingIds.add(item.id)
                }
              }
            }
          } catch (mineErr) {
            console.debug('Failed to fetch personal tickets with mine=true:', mineErr.message)
          }
        }

        if (!cancelled && Array.isArray(combinedRaw)) {
          const normalized = combinedRaw.map((item) => normalizeBackendTicket(item, user))
          setTickets(normalized)
          setBackendConnected(true)
        }
      } catch (err) {
        if (!cancelled) {
          console.debug('Ticket service not reachable via Gateway; using local demo storage.', err.message)
        }
      }
    }

    fetchBackendTickets()
    return () => {
      cancelled = true
    }
  }, [user])

  useEffect(() => {
    try {
      window.localStorage.setItem(TICKETS_STORAGE_KEY, JSON.stringify(tickets))
    } catch {
      // Demo ticket changes remain available in memory if local storage is unavailable.
    }
  }, [tickets])

  useEffect(() => {
    function syncTickets(event) {
      if (event.key !== TICKETS_STORAGE_KEY || !event.newValue) return
      try {
        const incoming = JSON.parse(event.newValue)
        if (Array.isArray(incoming)) setTickets(incoming)
      } catch {
        // Ignore incomplete storage writes and keep the current ticket state.
      }
    }
    window.addEventListener('storage', syncTickets)
    return () => window.removeEventListener('storage', syncTickets)
  }, [])

  useEffect(() => {
    function applyRealtimeTicket(event) {
      const incomingTicket = event.detail
      if (!incomingTicket?.id) return
      setTickets((current) => current.some((ticket) => ticket.id === incomingTicket.id)
        ? current.map((ticket) => ticket.id === incomingTicket.id ? incomingTicket : ticket)
        : [incomingTicket, ...current])
    }
    window.addEventListener(TICKET_UPDATED_EVENT, applyRealtimeTicket)
    return () => window.removeEventListener(TICKET_UPDATED_EVENT, applyRealtimeTicket)
  }, [])

  async function createTicket(formValues) {
    const now = new Date().toISOString()
    const classification = classifyTicket(formValues)
    const localId = nextTicketId(tickets)

    const payload = {
      ...formValues,
      ...classification,
      status: 'Open',
      requester: user?.name || 'Anonymous',
      requesterEmail: user?.email || '',
      requesterId: user?.id,
      assignee: null,
      createdAt: now,
      updatedAt: now,
      activity: [{ author: user?.name || 'User', message: 'Submitted this request for AI triage.', createdAt: now, kind: 'created' }],
    }

    try {
      const response = await api.post('/v1/tickets', {
        title: formValues.title,
        description: formValues.description,
      })
      if (response.data && response.data.id) {
        const created = normalizeBackendTicket(response.data, user)
        setTickets((current) => [created, ...current.filter((t) => t.id !== created.id)])
        setBackendConnected(true)
        return created
      }
    } catch (err) {
      console.debug('Ticket creation fallback to local storage:', err.message)
    }

    // Local fallback
    const localTicket = { ...payload, id: localId }
    setTickets((current) => [localTicket, ...current])
    return localTicket
  }

  async function updateTicket(ticketId, updates) {
    const now = new Date().toISOString()
    const existingTicket = tickets.find((ticket) => ticket.id === ticketId)

    if (existingTicket && updates.assignee && updates.assignee !== existingTicket.assignee) {
      addMockNotification({
        userEmail: existingTicket.requesterEmail,
        title: 'Your ticket was assigned',
        message: `${updates.assignee} picked up ${existingTicket.id} and will take a look.`,
      })
    }

    if (existingTicket && updates.status === 'Resolved' && existingTicket.status !== 'Resolved') {
      addMockNotification({
        userEmail: existingTicket.requesterEmail,
        title: 'Action required: Confirm ticket resolution',
        message: `Support has marked ${existingTicket.id} as resolved. Please review and confirm the resolution.`,
      })
    }

    // Extract raw numeric backend ID if exists (e.g. "SD-1" -> 1)
    const rawId = typeof ticketId === 'string' && ticketId.startsWith('SD-')
      ? parseInt(ticketId.replace('SD-', ''), 10)
      : ticketId

    let backendUpdated = null
    // Try backend update via gateway
    try {
      if (updates.assignee && updates.assignee !== existingTicket?.assignee) {
        const res = await api.post(`/v1/tickets/${rawId}/claim`)
        if (res.data) backendUpdated = normalizeBackendTicket(res.data, user)
      } else if (updates.status === 'Closed' && existingTicket?.status === 'Resolved') {
        const res = await api.post(`/v1/tickets/${rawId}/verify`, { accepted: true })
        if (res.data) backendUpdated = normalizeBackendTicket(res.data, user)
      } else if (updates.status === 'In Progress' && existingTicket?.status === 'Resolved') {
        const res = await api.post(`/v1/tickets/${rawId}/verify`, { accepted: false })
        if (res.data) backendUpdated = normalizeBackendTicket(res.data, user)
      } else if (updates.status) {
        const res = await api.patch(`/v1/tickets/${rawId}/status`, { status: toBackendStatus(updates.status) })
        if (res.data) backendUpdated = normalizeBackendTicket(res.data, user)
      }
    } catch (err) {
      console.debug('Ticket update fallback to local state:', err.message)
    }

    if (backendUpdated) {
      setTickets((current) => current.map((ticket) => (ticket.id === ticketId || ticket.id === backendUpdated.id ? backendUpdated : ticket)))
      return backendUpdated
    }

    setTickets((current) => current.map((ticket) => {
      if (ticket.id !== ticketId) return ticket

      const statusActivity = updates.status === 'Resolved'
        ? 'Marked as resolved. Waiting for requester acknowledgement.'
        : updates.status === 'Closed'
          ? 'Acknowledged the resolution and closed the ticket.'
          : updates.status === 'In Progress' && updates.reopenReason
            ? `Reopened ticket: ${updates.reopenReason}`
            : updates.status === 'Waiting on employee'
              ? 'Requested an update from the employee.'
              : `Status changed to ${updates.status}.`
      const activity = updates.status && updates.status !== ticket.status
        ? [{ author: user.name, message: statusActivity, createdAt: now, kind: 'status' }, ...ticket.activity]
        : updates.assignee && updates.assignee !== ticket.assignee
          ? [{ author: user.name, message: `Assigned to ${updates.assignee}.`, createdAt: now, kind: 'assignment' }, ...ticket.activity]
          : ticket.activity

      return {
        ...ticket,
        ...updates,
        ...(updates.status === 'Closed' ? { acknowledgedAt: now } : {}),
        activity,
        updatedAt: now,
      }
    }))
  }

  async function addComment(ticketId, message, author) {
    const now = new Date().toISOString()

    // Try backend comment endpoint
    try {
      await api.post(`/v1/tickets/${ticketId}/comments`, { message, author })
    } catch (err) {
      console.debug('Ticket comment fallback to local state:', err.message)
    }

    setTickets((current) => current.map((ticket) => ticket.id === ticketId
      ? {
          ...ticket,
          updatedAt: now,
          activity: [{ author, message, createdAt: now, kind: 'comment' }, ...ticket.activity],
        }
      : ticket))
  }

  const value = {
    tickets,
    createTicket,
    updateTicket,
    addComment,
    backendConnected,
  }

  return <TicketContext.Provider value={value}>{children}</TicketContext.Provider>
}
