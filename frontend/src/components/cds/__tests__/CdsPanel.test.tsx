import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '../../../test/test-utils'
import CdsPanel from '../CdsPanel'

// Stub heavy MUI icon barrel.
vi.mock('@mui/icons-material', () => {
  const Stub = () => null
  return new Proxy(
    { default: Stub },
    { get: (_t, prop) => (prop === '__esModule' ? true : Stub) },
  )
})

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
  Trans: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}))

// Each tab panel just renders a marker so we don't load the actual heavy
// children (Sandbox, Manage, etc.).
vi.mock('../InvokeServicePanel', () => ({ default: () => <div>stub-invoke</div> }))
vi.mock('../ManageServicesPanel', () => ({ default: () => <div>stub-manage</div> }))
vi.mock('../AnalyticsPanel', () => ({ default: () => <div>stub-analytics</div> }))
vi.mock('../SandboxPanel', () => ({ default: () => <div>stub-sandbox</div> }))
vi.mock('../ApiKeyManager', () => ({ default: () => <div>stub-keys</div> }))
vi.mock('../RecentInvocationsPanel', () => ({ default: () => <div>stub-recent</div> }))

describe('CdsPanel — PAT-132 admin tab visibility (P2)', () => {
  it('renders the admin-only Recent Invocations tab when role is ADMIN', () => {
    render(<CdsPanel />, {
      preloadedState: {
        auth: { user: { id: 1, username: 'admin', role: 'ADMIN' }, token: 't' },
      },
    })
    // Tab labels resolve to i18n keys under the mock; the admin-only one is
    // 'panel.tabRecent'.
    expect(screen.getByRole('tab', { name: /panel\.tabRecent/ })).toBeInTheDocument()
  })

  it('hides the Recent Invocations tab for non-admin roles', () => {
    render(<CdsPanel />, {
      preloadedState: {
        auth: { user: { id: 2, username: 'doc', role: 'USER' }, token: 't' },
      },
    })
    expect(screen.queryByRole('tab', { name: /panel\.tabRecent/ })).toBeNull()
  })
})
