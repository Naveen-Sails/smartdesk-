import { createContext } from 'react'

export const TicketContext = createContext(null)
export const TICKET_UPDATED_EVENT = 'smartdesk:ticket-updated'

export function emitTicketUpdated(ticket) {
  if (typeof window === 'undefined' || !ticket?.id) return
  window.dispatchEvent(new CustomEvent(TICKET_UPDATED_EVENT, { detail: ticket }))
}

/**
 * Accurately determines if a ticket was raised by the given user.
 * Prioritizes requesterId (UUID), guards against cross-user name mismatch,
 * and matches by verified requesterEmail / name.
 */
export function isUserTicket(ticket, user) {
  if (!ticket || !user) return false

  // 1. Primary check: Requester ID (UUID)
  if (ticket.requesterId && user.id) {
    return String(ticket.requesterId).toLowerCase() === String(user.id).toLowerCase()
  }

  // 2. Reject if the ticket has a requester name that clearly belongs to someone else
  if (ticket.requester && user.name && ticket.requester !== 'Employee' && ticket.requester !== 'Anonymous') {
    if (ticket.requester.trim().toLowerCase() !== user.name.trim().toLowerCase()) {
      return false
    }
  }

  // 3. Email match (case-insensitive)
  if (ticket.requesterEmail && user.email) {
    return ticket.requesterEmail.trim().toLowerCase() === user.email.trim().toLowerCase()
  }

  // 4. Fallback to name match (case-insensitive)
  if (ticket.requester && user.name) {
    return ticket.requester.trim().toLowerCase() === user.name.trim().toLowerCase()
  }

  return false
}
