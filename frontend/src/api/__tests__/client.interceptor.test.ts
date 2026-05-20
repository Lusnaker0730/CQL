import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { api } from '../client'

// BUG-117 regression: 403 from an ADMIN-only endpoint must NOT log the user out.
// Originally api/client.ts treated any 403 the same as 401, so a non-admin
// who landed on a page that probed an admin endpoint (e.g. EditorPage →
// /api/settings/ai-status) had their token wiped and was bounced to /login
// immediately after a successful login. See conversation log on 2026-05-20.
const server = setupServer(
  http.get('/api/settings/ai-status', () =>
    HttpResponse.json({ error: 'Access Denied' }, { status: 403 })
  ),
  http.post('/api/auth/login', () =>
    HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 })
  ),
  http.get('/api/cql/libraries', () =>
    HttpResponse.json({ error: 'Token expired' }, { status: 401 })
  ),
)

describe('api/client response interceptor', () => {
  beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
  afterEach(() => server.resetHandlers())
  afterAll(() => server.close())

  it('keeps the session intact when a non-auth endpoint returns 403', async () => {
    localStorage.setItem('token', 'fake-token')
    localStorage.setItem('user', JSON.stringify({ username: 'u', role: 'USER' }))
    const hrefSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { pathname: '/', set href(v: string) { hrefSpy(v) } },
    })

    await expect(api.get('/settings/ai-status')).rejects.toMatchObject({
      response: { status: 403 },
    })

    expect(localStorage.getItem('token')).toBe('fake-token')
    expect(localStorage.getItem('user')).not.toBeNull()
    expect(hrefSpy).not.toHaveBeenCalled()
  })

  it('clears storage and redirects on 401 from an auth endpoint', async () => {
    localStorage.setItem('token', 'fake-token')
    localStorage.setItem('user', JSON.stringify({ username: 'u', role: 'USER' }))
    const hrefSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { pathname: '/', set href(v: string) { hrefSpy(v) } },
    })

    await expect(
      api.post('/auth/login', { username: 'u', password: 'bad' })
    ).rejects.toMatchObject({ response: { status: 401 } })

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
    expect(hrefSpy).toHaveBeenCalledWith('/login')
  })
})
