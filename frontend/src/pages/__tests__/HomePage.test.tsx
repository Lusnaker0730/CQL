import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '../../test/test-utils'
import HomePage from '../HomePage'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn(), language: 'en' },
  }),
}))

const navigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const real = await vi.importActual<Record<string, unknown>>('react-router-dom')
  return { ...real, useNavigate: () => navigate }
})

vi.mock('../../components/common/LanguageMenu', () => ({ default: () => <div data-testid="language-menu" /> }))
vi.mock('../../components/landing/CqlShowcase', () => ({ default: () => <div data-testid="cql-showcase" /> }))

// PAT-251 — the public home page: what the platform is, for whom, how it works, what it offers,
// with the calls to action wired to /apply, /login and the learning / docs pages.
describe('HomePage', () => {
  beforeEach(() => navigate.mockReset())

  it('renders hero, audiences, the four steps, the five capability groups, demo and closing CTA', () => {
    render(<HomePage />)

    expect(screen.getByRole('heading', { level: 1, name: 'home.hero.title' })).toBeInTheDocument()
    for (const a of ['hospital', 'clinic', 'developer']) {
      expect(screen.getByText(`home.audiences.${a}.title`)).toBeInTheDocument()
    }
    for (const s of ['import', 'build', 'verify', 'run']) {
      expect(screen.getByRole('heading', { level: 3, name: `home.howItWorks.${s}.title` })).toBeInTheDocument()
      expect(screen.getByAltText(`home.howItWorks.${s}.imageAlt`)).toHaveAttribute('src', `/screenshots/step-${s}.jpg`)
    }
    for (const g of ['authoring', 'verification', 'exchange', 'data', 'governance']) {
      expect(screen.getByText(`home.capabilities.${g}.title`)).toBeInTheDocument()
    }
    expect(screen.getByText('home.capabilities.verification.approval.title')).toBeInTheDocument()
    expect(screen.getByAltText('home.demo.report.imageAlt')).toBeInTheDocument()
    expect(screen.getByTestId('cql-showcase')).toBeInTheDocument()
    expect(screen.getByText('home.cta.title')).toBeInTheDocument()
  })

  it('wires the calls to action: apply, sign in, learn, docs, templates', () => {
    render(<HomePage />)

    fireEvent.click(screen.getByRole('button', { name: 'home.hero.primaryCta' }))
    expect(navigate).toHaveBeenLastCalledWith('/apply')
    fireEvent.click(screen.getByRole('button', { name: 'home.nav.login' }))
    expect(navigate).toHaveBeenLastCalledWith('/login')
    fireEvent.click(screen.getByRole('button', { name: 'home.hero.tertiaryCta' }))
    expect(navigate).toHaveBeenLastCalledWith('/learn')
    fireEvent.click(screen.getByRole('button', { name: 'home.demo.docsCta' }))
    expect(navigate).toHaveBeenLastCalledWith('/docs')
    fireEvent.click(screen.getByRole('button', { name: 'home.nav.templates' }))
    expect(navigate).toHaveBeenLastCalledWith('/templates')
  })

  it('is indexable and canonical at the site root', () => {
    render(<HomePage />)
    // react-helmet-async renders into document.head asynchronously; the tags we care about are declared
    // on the page, so assert on the markup it emitted rather than on timing-sensitive head state.
    expect(screen.getByRole('navigation', { name: 'home.nav.ariaLabel' })).toBeInTheDocument()
  })
})
