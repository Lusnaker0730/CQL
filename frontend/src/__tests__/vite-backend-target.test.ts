import { describe, expect, it, vi } from 'vitest'
import { resolveBackendTarget } from '../../../vite-backend-target'

describe('resolveBackendTarget', () => {
  it('skips error responses and falls back to the first healthy backend', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString()
      if (url.startsWith('http://localhost:8080')) {
        return new Response(JSON.stringify({
          startupTime: '2026-06-04T00:00:00Z',
          commitSha: 'abc123',
          version: '1.0.0',
        }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        })
      }

      return new Response('backend error', { status: 500 })
    })

    await expect(resolveBackendTarget(fetchImpl as unknown as typeof fetch))
      .resolves.toBe('http://localhost:8080')
  })

  it('prefers the explicit proxy target when set', async () => {
    const originalTarget = process.env.VITE_BACKEND_PROXY_TARGET
    process.env.VITE_BACKEND_PROXY_TARGET = 'http://example.test:9000'

    const fetchImpl = vi.fn()

    try {
      await expect(resolveBackendTarget(fetchImpl as unknown as typeof fetch))
        .resolves.toBe('http://example.test:9000')
      expect(fetchImpl).not.toHaveBeenCalled()
    } finally {
      if (originalTarget === undefined) {
        delete process.env.VITE_BACKEND_PROXY_TARGET
      } else {
        process.env.VITE_BACKEND_PROXY_TARGET = originalTarget
      }
    }
  })
})