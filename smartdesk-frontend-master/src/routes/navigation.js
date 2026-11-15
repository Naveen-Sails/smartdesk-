import { BarChart3, FileText, Inbox, LayoutDashboard, Plus, Ticket, Users } from 'lucide-react'
import { ROLES } from '../context/roles.js'

export const ROLE_NAVIGATION = {
  [ROLES.EMPLOYEE]: {
    label: 'Employee',
    homePath: '/employee',
    links: [
      { label: 'Overview', path: '/employee', icon: LayoutDashboard },
      { label: 'My tickets', path: '/employee/tickets', icon: Ticket },
      { label: 'New request', path: '/employee/new', icon: Plus },
    ],
  },
  [ROLES.AGENT]: {
    label: 'Support Agent',
    homePath: '/agent',
    links: [
      { label: 'Overview', path: '/agent', icon: LayoutDashboard },
      { label: 'Ticket queue', path: '/agent/queue', icon: Inbox },
      { label: 'My assigned tickets', path: '/agent/tickets', icon: Ticket },
      { label: 'My requests', path: '/agent/my-requests', icon: FileText },
      { label: 'New request', path: '/agent/new', icon: Plus },
    ],
  },
  [ROLES.MANAGER]: {
    label: 'Manager',
    homePath: '/manager',
    links: [
      { label: 'Overview', path: '/manager', icon: LayoutDashboard },
      { label: 'Team workload', path: '/manager/team', icon: Users },
      { label: 'All tickets', path: '/manager/tickets', icon: Ticket },
      { label: 'Reports', path: '/manager/reports', icon: BarChart3 },
      { label: 'My requests', path: '/manager/my-requests', icon: FileText },
      { label: 'New request', path: '/manager/new', icon: Plus },
    ],
  },
  [ROLES.ADMIN]: {
    label: 'Administrator',
    homePath: '/admin',
    links: [
      { label: 'Overview', path: '/admin', icon: LayoutDashboard },
      { label: 'Team workload', path: '/admin/team', icon: Users },
      { label: 'All tickets', path: '/admin/tickets', icon: Ticket },
      { label: 'Reports', path: '/admin/reports', icon: BarChart3 },
      { label: 'My requests', path: '/admin/my-requests', icon: FileText },
      { label: 'New request', path: '/admin/new', icon: Plus },
    ],
  },
}

