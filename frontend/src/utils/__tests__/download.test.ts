import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { downloadBlob } from '../download'

// jsdom does not ship URL.createObjectURL — stub it on the URL prototype
function stubObjectUrl(returnUrl = 'blob:fake-url') {
  const createSpy = vi.fn(() => returnUrl)
  const revokeSpy = vi.fn()
  // Replace directly; URL.createObjectURL is missing in jsdom
  ;(URL as unknown as { createObjectURL: typeof createSpy }).createObjectURL = createSpy
  ;(URL as unknown as { revokeObjectURL: typeof revokeSpy }).revokeObjectURL = revokeSpy
  return { createSpy, revokeSpy }
}

describe('downloadBlob', () => {
  let restoreCreateElement: (() => void) | null = null

  beforeEach(() => {
    // Default: leave createElement alone — individual tests install stubs as needed
    restoreCreateElement = null
  })

  afterEach(() => {
    vi.restoreAllMocks()
    if (restoreCreateElement) restoreCreateElement()
    delete (URL as unknown as { createObjectURL?: unknown }).createObjectURL
    delete (URL as unknown as { revokeObjectURL?: unknown }).revokeObjectURL
  })

  it('creates object URL, triggers click on anchor, and revokes URL', () => {
    const { createSpy, revokeSpy } = stubObjectUrl('blob:fake-url')
    const clickSpy = vi.fn()

    const realCreateElement = document.createElement.bind(document)
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = realCreateElement(tag)
      if (tag === 'a') {
        Object.defineProperty(el, 'click', { value: clickSpy })
      }
      return el
    })
    restoreCreateElement = () => createElementSpy.mockRestore()

    const blob = new Blob(['hello'], { type: 'text/plain' })
    downloadBlob(blob, 'file.txt')

    expect(createSpy).toHaveBeenCalledWith(blob)
    expect(clickSpy).toHaveBeenCalledOnce()
    expect(revokeSpy).toHaveBeenCalledWith('blob:fake-url')
  })

  it('sets download filename on the anchor', () => {
    stubObjectUrl('blob:x')

    let capturedAnchor: HTMLAnchorElement | null = null
    const realCreateElement = document.createElement.bind(document)
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = realCreateElement(tag)
      if (tag === 'a') {
        capturedAnchor = el as HTMLAnchorElement
        Object.defineProperty(el, 'click', { value: vi.fn() })
      }
      return el
    })
    restoreCreateElement = () => createElementSpy.mockRestore()

    downloadBlob(new Blob(['x']), 'report.csv')

    expect(capturedAnchor).not.toBeNull()
    expect(capturedAnchor!.download).toBe('report.csv')
    expect(capturedAnchor!.href).toContain('blob:')
  })
})
