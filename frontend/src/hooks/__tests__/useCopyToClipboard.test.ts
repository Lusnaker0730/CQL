import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useCopyToClipboard } from '../useCopyToClipboard'

const mockShowNotification = vi.fn()

vi.mock('../useNotification', () => ({
  useNotification: () => ({
    showNotification: mockShowNotification,
  }),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}))

describe('useCopyToClipboard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should copy text to clipboard and show success notification', async () => {
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    })

    const { result } = renderHook(() => useCopyToClipboard())

    await act(async () => {
      await result.current('hello world')
    })

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('hello world')
    expect(mockShowNotification).toHaveBeenCalledWith('common.copiedToClipboard', 'success', 2000)
  })

  it('should show error notification when clipboard fails', async () => {
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) },
    })

    const { result } = renderHook(() => useCopyToClipboard())

    await act(async () => {
      await result.current('text')
    })

    expect(mockShowNotification).toHaveBeenCalledWith('common.copyFailed', 'error', 2000)
  })

  it('should copy empty string', async () => {
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    })

    const { result } = renderHook(() => useCopyToClipboard())

    await act(async () => {
      await result.current('')
    })

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('')
    expect(mockShowNotification).toHaveBeenCalledWith('common.copiedToClipboard', 'success', 2000)
  })
})
