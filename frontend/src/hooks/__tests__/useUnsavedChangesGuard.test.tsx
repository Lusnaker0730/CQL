import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useUnsavedChangesGuard } from '../useUnsavedChangesGuard'

describe('useUnsavedChangesGuard', () => {
  it('should not block when not dirty', () => {
    renderHook(() => useUnsavedChangesGuard(false))

    const event = new Event('beforeunload') as BeforeUnloadEvent
    Object.defineProperty(event, 'preventDefault', { value: vi.fn() })

    window.dispatchEvent(event)

    expect(event.preventDefault).not.toHaveBeenCalled()
  })

  it('should block navigation when dirty', () => {
    renderHook(() => useUnsavedChangesGuard(true))

    const event = new Event('beforeunload') as BeforeUnloadEvent
    const preventDefaultMock = vi.fn()
    Object.defineProperty(event, 'preventDefault', { value: preventDefaultMock, writable: true })

    window.dispatchEvent(event)

    expect(preventDefaultMock).toHaveBeenCalled()
  })

  it('should set returnValue when dirty', () => {
    renderHook(() => useUnsavedChangesGuard(true, 'Custom message'))

    const event = new Event('beforeunload') as BeforeUnloadEvent
    Object.defineProperty(event, 'preventDefault', { value: vi.fn() })
    Object.defineProperty(event, 'returnValue', { value: '', writable: true })

    window.dispatchEvent(event)

    expect(event.returnValue).toBe('Custom message')
  })

  it('should remove listener on unmount', () => {
    const spy = vi.spyOn(window, 'removeEventListener')
    const { unmount } = renderHook(() => useUnsavedChangesGuard(true))

    unmount()

    expect(spy).toHaveBeenCalledWith('beforeunload', expect.any(Function))
    spy.mockRestore()
  })
})
