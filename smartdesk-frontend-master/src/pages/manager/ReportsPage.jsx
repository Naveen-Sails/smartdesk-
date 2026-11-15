import { useMemo } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { CheckCircle2, CircleAlert, Clock, Inbox, Ticket } from 'lucide-react'
import { useTickets } from '../../features/tickets/useTickets.js'
import { useAuth } from '../../features/auth/useAuth.js'
import { ROLES } from '../../context/roles.js'

const CHART_COLORS = ['#3b82f6', '#f59e0b', '#8b5cf6', '#10b981', '#6b7280']

function ReportsPage() {
  const { tickets } = useTickets()
  const { user } = useAuth()

  const visibleTickets = useMemo(() => {
    return user.role === ROLES.MANAGER
      ? tickets.filter((ticket) => ticket.department === user.department)
      : tickets
  }, [tickets, user.department, user.role])

  const statusCounts = useMemo(() => ({
    open: visibleTickets.filter((t) => t.status?.toLowerCase() === 'open').length,
    inProgress: visibleTickets.filter((t) => t.status?.toLowerCase() === 'in progress').length,
    waiting: visibleTickets.filter((t) => t.status?.toLowerCase() === 'waiting on employee').length,
    resolved: visibleTickets.filter((t) => t.status?.toLowerCase() === 'resolved').length,
    closed: visibleTickets.filter((t) => t.status?.toLowerCase() === 'closed').length,
  }), [visibleTickets])

  const statusData = useMemo(() => [
    { name: 'Open', value: statusCounts.open, fill: '#3b82f6' },
    { name: 'In Progress', value: statusCounts.inProgress, fill: '#f59e0b' },
    { name: 'Waiting on employee', value: statusCounts.waiting, fill: '#8b5cf6' },
    { name: 'Resolved', value: statusCounts.resolved, fill: '#10b981' },
    { name: 'Closed', value: statusCounts.closed, fill: '#6b7280' },
  ], [statusCounts])

  const statusChartData = useMemo(() => [
    { status: 'Open', count: statusCounts.open, fill: '#3b82f6' },
    { status: 'In Progress', count: statusCounts.inProgress, fill: '#f59e0b' },
    { status: 'Waiting', count: statusCounts.waiting, fill: '#8b5cf6' },
    { status: 'Resolved', count: statusCounts.resolved, fill: '#10b981' },
    { status: 'Closed', count: statusCounts.closed, fill: '#6b7280' },
  ], [statusCounts])

  const categoryData = useMemo(() => {
    const defaultDepts = user.role === ROLES.MANAGER && user.department
      ? [user.department]
      : ['IT', 'HR', 'Facilities', 'Finance', 'Engineering']
    const foundCategories = visibleTickets
      .map((t) => t.category || t.department || 'IT')
      .filter(Boolean)
    const allCategories = Array.from(new Set([...defaultDepts, ...foundCategories]))
    return allCategories.map((category) => ({
      category,
      total: visibleTickets.filter(
        (t) => (t.category || t.department || '').toLowerCase() === category.toLowerCase()
      ).length,
    }))
  }, [user.department, user.role, visibleTickets])

  return (
    <section className="mx-auto w-full max-w-6xl px-6 py-9 sm:px-10 sm:py-11">
      <p className="text-sm font-medium text-neutral-500">
        {user.role === ROLES.ADMIN ? 'Admin workspace' : `${user.department} manager workspace`}
      </p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-neutral-900">Reports & Analytics</h1>
      <p className="mt-2 text-sm text-neutral-500">
        Explore a comprehensive breakdown of support requests across categories, workflows, and resolution states.
      </p>

      {/* Summary Stat Cards */}
      <div className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        <div className="rounded-2xl border border-neutral-200 bg-white p-4">
          <div className="flex items-center justify-between text-neutral-500">
            <span className="text-xs font-medium">Total Tickets</span>
            <Ticket size={16} />
          </div>
          <p className="mt-3 text-2xl font-semibold text-neutral-900">{visibleTickets.length}</p>
        </div>
        <div className="rounded-2xl border border-neutral-200 bg-white p-4">
          <div className="flex items-center justify-between text-blue-600">
            <span className="text-xs font-medium text-neutral-500">Open</span>
            <Inbox size={16} />
          </div>
          <p className="mt-3 text-2xl font-semibold text-neutral-900">{statusCounts.open}</p>
        </div>
        <div className="rounded-2xl border border-neutral-200 bg-white p-4">
          <div className="flex items-center justify-between text-amber-600">
            <span className="text-xs font-medium text-neutral-500">In Progress</span>
            <Clock size={16} />
          </div>
          <p className="mt-3 text-2xl font-semibold text-neutral-900">{statusCounts.inProgress}</p>
        </div>
        <div className="rounded-2xl border border-neutral-200 bg-white p-4">
          <div className="flex items-center justify-between text-emerald-600">
            <span className="text-xs font-medium text-neutral-500">Resolved</span>
            <CircleAlert size={16} />
          </div>
          <p className="mt-3 text-2xl font-semibold text-neutral-900">{statusCounts.resolved}</p>
        </div>
        <div className="rounded-2xl border border-neutral-200 bg-white p-4 col-span-2 sm:col-span-1">
          <div className="flex items-center justify-between text-neutral-600">
            <span className="text-xs font-medium text-neutral-500">Closed</span>
            <CheckCircle2 size={16} />
          </div>
          <p className="mt-3 text-2xl font-semibold text-neutral-900">{statusCounts.closed}</p>
        </div>
      </div>

      <div className="mt-7 grid gap-6 lg:grid-cols-2">
        {/* Bar Graph 1: Tickets by Category */}
        <section className="rounded-2xl border border-neutral-200 bg-white p-5 sm:p-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-sm font-semibold text-neutral-900">Tickets by Category</h2>
              <p className="mt-0.5 text-xs text-neutral-500">Distribution across service categories</p>
            </div>
            <span className="rounded-full bg-neutral-100 px-2.5 py-0.5 text-xs font-medium text-neutral-600">
              Bar Graph
            </span>
          </div>
          <div className="mt-5 h-72 w-full" role="img" aria-label="Bar chart showing tickets by category">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={categoryData} margin={{ top: 8, right: 8, left: -20, bottom: 4 }}>
                <CartesianGrid stroke="#f0f0f0" strokeDasharray="3 3" vertical={false} />
                <XAxis axisLine={false} dataKey="category" tick={{ fill: '#737373', fontSize: 11 }} tickLine={false} />
                <YAxis allowDecimals={false} axisLine={false} tick={{ fill: '#a3a3a3', fontSize: 11 }} tickLine={false} />
                <Tooltip
                  contentStyle={{ borderRadius: '8px', border: '1px solid #e5e5e5', fontSize: '12px' }}
                  cursor={{ fill: '#f8f8f8' }}
                />
                <Bar dataKey="total" fill="#3b82f6" radius={[5, 5, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>

        {/* Bar Graph 2: Tickets by Status */}
        <section className="rounded-2xl border border-neutral-200 bg-white p-5 sm:p-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-sm font-semibold text-neutral-900">Tickets by Status</h2>
              <p className="mt-0.5 text-xs text-neutral-500">Current volume across lifecycle stages</p>
            </div>
            <span className="rounded-full bg-neutral-100 px-2.5 py-0.5 text-xs font-medium text-neutral-600">
              Bar Graph
            </span>
          </div>
          <div className="mt-5 h-72 w-full" role="img" aria-label="Bar chart showing ticket count by status">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={statusChartData} margin={{ top: 8, right: 8, left: -20, bottom: 4 }}>
                <CartesianGrid stroke="#f0f0f0" strokeDasharray="3 3" vertical={false} />
                <XAxis axisLine={false} dataKey="status" tick={{ fill: '#737373', fontSize: 11 }} tickLine={false} />
                <YAxis allowDecimals={false} axisLine={false} tick={{ fill: '#a3a3a3', fontSize: 11 }} tickLine={false} />
                <Tooltip
                  contentStyle={{ borderRadius: '8px', border: '1px solid #e5e5e5', fontSize: '12px' }}
                  cursor={{ fill: '#f8f8f8' }}
                />
                <Bar dataKey="count" radius={[5, 5, 0, 0]}>
                  {statusChartData.map((entry) => (
                    <Cell fill={entry.fill} key={entry.status} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>

        {/* Status Distribution Donut Chart */}
        <section className="rounded-2xl border border-neutral-200 bg-white p-5 sm:p-6 lg:col-span-2">
          <h2 className="text-sm font-semibold text-neutral-900">Status Distribution Breakdown</h2>
          <p className="mt-0.5 text-xs text-neutral-500">Proportional share of total support volume</p>
          <div className="mt-5 grid items-center gap-6 md:grid-cols-[1fr_300px]">
            <div className="h-64 w-full" role="img" aria-label="Pie chart showing ticket status distribution">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    cx="50%"
                    cy="50%"
                    data={statusData}
                    dataKey="value"
                    innerRadius={62}
                    nameKey="name"
                    outerRadius={96}
                    paddingAngle={3}
                  >
                    {statusData.map((entry, index) => (
                      <Cell fill={CHART_COLORS[index % CHART_COLORS.length]} key={entry.name} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ borderRadius: '8px', border: '1px solid #e5e5e5', fontSize: '12px' }}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="space-y-3">
              {statusData.map((item, index) => (
                <div className="flex items-center justify-between text-xs text-neutral-600 border-b border-neutral-100 pb-2 last:border-0" key={item.name}>
                  <div className="flex items-center gap-2">
                    <span className="size-2.5 rounded-full" style={{ backgroundColor: CHART_COLORS[index % CHART_COLORS.length] }} />
                    <span className="font-medium text-neutral-800">{item.name}</span>
                  </div>
                  <span className="font-semibold text-neutral-900">
                    {item.value} ({visibleTickets.length ? Math.round((item.value / visibleTickets.length) * 100) : 0}%)
                  </span>
                </div>
              ))}
            </div>
          </div>
        </section>
      </div>
    </section>
  )
}

export default ReportsPage
