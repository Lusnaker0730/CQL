import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { api } from '../client'

// BUG-117 regression: 403 from an ADMIN-only endpoint must NOT log the user out.
// Originally api/client.ts treated any 403 the same as 401, so a non-admin
// who landed on a page that probed an admin endpoint (e.g. EditorPage →
// /api/settings/ai-status) had their token wiped and was bounced to /login
// immediately after a successful login. See conversation log on 2026-05-20.

// jsdom's default location is http://localhost/, so we pin the axios baseURL
// to a matching absolute URL — MSW resolves the path the same way regardless,
// and this avoids `TypeError: Invalid URL` when axios concatenates the path.
const BASE = 'http://localhost/api'

const server = setupServer(
  http.get(`${BASE}/settings/ai-status`, () =>
    HttpResponse.json({ error: 'Access Denied' }, { status: 403 })
  ),
  http.post(`${BASE}/auth/login`, () =>
    HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 })
  ),
)

describe('api/client response interceptor', () => {
  let originalBaseURL: string | undefined
  let hrefSpy: ReturnType<typeof vi.fn>

  beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
  afterAll(() => {
    server.close()
    if (originalBaseURL !== undefined) api.defaults.baseURL = originalBaseURL
  })
  beforeEach(() => {
    originalBaseURL = api.defaults.baseURL
    api.defaults.baseURL = BASE
    localStorage.setItem('token', 'fake-token')
    localStorage.setItem('user', JSON.stringify({ username: 'u', role: 'USER' }))
    hrefSpy = vi.fn()
    // Only override the href setter so the URL constructor still sees a sane
    // window.location (jsdom defaults to http://localhost/) for everything else.
    const proto = Object.getPrototypeOf(window.location)
    Object.defineProperty(proto, 'href', {
      configurable: true,
      get: () => 'http://localhost/',
      set: hrefSpy,
    })
  })
  afterEach(() => {
    server.resetHandlers()
    if (originalBaseURL !== undefined) api.defaults.baseURL = originalBaseURL
  })

  it('keeps the session intact when a non-auth endpoint returns 403', async () => {
    await expect(api.get('/settings/ai-status')).rejects.toMatchObject({
      response: { status: 403 },
    })

    expect(localStorage.getItem('token')).toBe('fake-token')
    expect(localStorage.getItem('user')).not.toBeNull()
    expect(hrefSpy).not.toHaveBeenCalled()
  })

  it('clears storage and redirects on 401 from an auth endpoint', async () => {
    await expect(
      api.post('/auth/login', { username: 'u', password: 'bad' })
    ).rejects.toMatchObject({ response: { status: 401 } })

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
    expect(hrefSpy).toHaveBeenCalledWith('/login')
  })
})
