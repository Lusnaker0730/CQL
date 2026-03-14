import { describe, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useNotification } from '../useNotification'
import { NotificationProvider } from '../../contexts/NotificationContext'
import type { ReactNode } from 'react'

function wrapper({ children }: { children: ReactNode }) {
  return <NotificationProvider>{children}</NotificationProvider>
}

describe('useNotification', () => {
  it('should throw when used outside provider', () => {
    expect(() => {
      renderHook(() => useNotification())
    }).toThrow('useNotification must be used within a NotificationProvider')
  })

  it('should show success notification', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })

    act(() => {
      result.current.showNotification('Success!', 'success')
    })

    expect(result.current.notifications).toHaveLength(1)
    expect(result.current.notifications[0].message).toBe('Success!')
    expect(result.current.notifications[0].severity).toBe('success')
  })

  it('should show error notification', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })

    act(() => {
      result.current.showNotification('Error!', 'error')
    })

    expect(result.current.notifications).toHaveLength(1)
    expect(result.current.notifications[0].severity).toBe('error')
  })

  it('should show warning notification', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })

    act(() => {
      result.current.showNotification('Warning!', 'warning')
    })

    expect(result.current.notifications[0].severity).toBe('warning')
  })

  it('should default to info severity', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })

    act(() => {
      result.current.showNotification('Info message')
    })

    expect(result.current.notifications[0].severity).toBe('info')
  })

  it('should remove notification by id', () => {
    const { result } = renderHook(() => useNotification(), { wrapper })

    act(() => {
      result.current.showNotification('Test')
    })

    const id = result.current.notifications[0].id

    act(() => {
      result.current.removeNotification(id)
    })

    expect(result.current.notifications).toHaveLength(0)
  })
})
