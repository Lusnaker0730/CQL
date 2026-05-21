import { describe, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useContext } from 'react'
import { NotificationContext, NotificationProvider } from '../NotificationContext'

function useNotification() {
  const ctx = useContext(NotificationContext)
  if (!ctx) throw new Error('NotificationContext not available')
  return ctx
}

function wrapper({ children }: { children: React.ReactNode }) {
  return <NotificationProvider>{children}</NotificationProvider>
}

describe('NotificationContext', () => {
  it('starts with empty notifications list', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })
    expect(result.current.notifications).toEqual([])
  })

  it('adds a notification with default info severity when severity omitted', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })
    act(() => result.current.showNotification('hello'))
    expect(result.current.notifications).toHaveLength(1)
    expect(result.current.notifications[0].message).toBe('hello')
    expect(result.current.notifications[0].severity).toBe('info')
  })

  it('respects explicit severity and duration', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })
    act(() => result.current.showNotification('boom', 'error', 1000))
    const n = result.current.notifications[0]
    expect(n.severity).toBe('error')
    expect(n.duration).toBe(1000)
  })

  it('assigns a unique id to each notification', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })
    act(() => {
      result.current.showNotification('first')
      result.current.showNotification('second')
    })
    const ids = result.current.notifications.map((n) => n.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('removes a notification by id', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })
    act(() => {
      result.current.showNotification('keep')
      result.current.showNotification('drop')
    })
    const targetId = result.current.notifications[1].id
    act(() => result.current.removeNotification(targetId))
    expect(result.current.notifications).toHaveLength(1)
    expect(result.current.notifications[0].message).toBe('keep')
  })

  it('removing a non-existent id is a no-op', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })
    act(() => result.current.showNotification('only'))
    const snapshot = [...result.current.notifications]
    act(() => result.current.removeNotification(99999))
    expect(result.current.notifications).toEqual(snapshot)
  })
})
