import { ArrowLeft, Send } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useTickets } from '../../features/tickets/useTickets.js'
import { useAuth } from '../../features/auth/useAuth.js'
import { ROLES } from '../../context/roles.js'
import { ROLE_NAVIGATION } from '../../routes/navigation.js'

function NewTicketPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { createTicket } = useTickets()
  const [form, setForm] = useState({ title: '', description: '' })

  const basePath =
    user?.role === ROLES.ADMIN
      ? '/admin'
      : user?.role === ROLES.MANAGER
        ? '/manager'
        : user?.role === ROLES.AGENT
          ? '/agent'
          : '/employee'

  const myRequestsPath = user?.role === ROLES.EMPLOYEE ? '/employee/tickets' : `${basePath}/my-requests`

  function updateField(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    try {
      const ticket = await createTicket(form)
      if (ticket && ticket.id) {
        toast.success(`${ticket.id} routed to ${ticket.department || 'IT'} support.`)
        navigate(`${basePath}/tickets/${ticket.id}`)
      } else {
        toast.error('Could not create ticket. Please try again.')
      }
    } catch (err) {
      toast.error('Error creating ticket: ' + (err.message || err))
    }
  }

  return (
    <section className="mx-auto w-full max-w-3xl px-6 py-9 sm:px-10 sm:py-11">
      <Link className="inline-flex items-center gap-2 text-sm text-neutral-500 hover:text-neutral-900" to={myRequestsPath}>
        <ArrowLeft size={16} /> Back to {user?.role === ROLES.EMPLOYEE ? 'my tickets' : 'my requests'}
      </Link>
      <div className="mt-6">
        <p className="text-sm font-medium text-neutral-500">{ROLE_NAVIGATION[user?.role]?.label || 'Employee'} workspace</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-neutral-900">Create a support request</h1>
        <p className="mt-2 text-sm text-neutral-500">Describe what you need help with. SmartDesk will suggest a department, category, summary, and priority.</p>
      </div>

      <form className="mt-6 space-y-5 rounded-2xl border border-neutral-200 bg-white p-5 sm:p-7" onSubmit={handleSubmit}>
        <label className="block space-y-2">
          <span className="text-sm font-medium text-neutral-800">Request title</span>
          <input
            className="h-11 w-full rounded-lg border border-neutral-200 px-3 text-sm outline-none placeholder:text-neutral-400 focus:border-neutral-400"
            maxLength={120}
            name="title"
            onChange={updateField}
            placeholder="For example, I need access to a tool"
            required
            value={form.title}
          />
        </label>

        <label className="block space-y-2">
          <span className="text-sm font-medium text-neutral-800">What happened?</span>
          <textarea
            className="min-h-36 w-full resize-y rounded-lg border border-neutral-200 px-3 py-3 text-sm outline-none placeholder:text-neutral-400 focus:border-neutral-400"
            maxLength={3000}
            name="description"
            onChange={updateField}
            placeholder="Include any details that can help the team understand the issue."
            required
            value={form.description}
          />
        </label>

        <div className="flex flex-col-reverse justify-between gap-3 border-t border-neutral-100 pt-5 sm:flex-row sm:items-center">
          <button className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-neutral-900 px-4 text-sm font-medium text-white transition hover:bg-neutral-700" type="submit">
            <Send size={15} /> Analyze and submit
          </button>
        </div>
      </form>
    </section>
  )
}

export default NewTicketPage
