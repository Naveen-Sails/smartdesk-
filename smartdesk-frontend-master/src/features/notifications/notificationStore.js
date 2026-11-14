const STORAGE_KEY = 'smartdesk-notifications'

function seedNotifications() {
  return []
}

function readAllNotifications() {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const parsed = JSON.parse(stored)
      if (Array.isArray(parsed)) return parsed
    }
    const seeded = seedNotifications()
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(seeded))
    return seeded
  } catch {
    return seedNotifications()
  }
}

function saveNotifications(notifications) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(notifications))
  } catch {
    // The notifications still update in the current in-memory session.
  }
  window.dispatchEvent(new CustomEvent('smartdesk:notifications-updated'))
}

export function loadNotifications() {
  return readAllNotifications()
}

export function addMockNotification(notification) {
  const current = readAllNotifications()
  const next = [{
    id: `notification-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: new Date().toISOString(),
    isRead: false,
    ...notification,
  }, ...current]
  saveNotifications(next)
}

export function markMockNotificationRead(notificationId) {
  const next = readAllNotifications().map((item) => item.id === notificationId ? { ...item, isRead: true } : item)
  saveNotifications(next)
}

export function markAllMockNotificationsRead(userEmail) {
  const next = readAllNotifications().map((item) => item.userEmail === userEmail ? { ...item, isRead: true } : item)
  saveNotifications(next)
}
