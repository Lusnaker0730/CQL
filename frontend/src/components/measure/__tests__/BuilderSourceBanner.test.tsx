import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'
import BuilderSourceBanner from '../BuilderSourceBanner'
import { measureApi } from '../../../api'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const navigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigate }
})

// PAT-238 — a measure published from the eCQM builder links back to it and says which side
// changed since that publish; a measure not built in the builder shows nothing.
describe('BuilderSourceBanner', () => {
  beforeEach(() => {
    navigate.mockReset()
    vi.restoreAllMocks()
  })

  it('renders nothing for a measure that was not built in the builder', async () => {
    vi.spyOn(measureApi, 'getBuilderSource').mockResolvedValue(null)
    render(<BuilderSourceBanner measureId={5} />)
    await new Promise((r) => setTimeout(r, 0))
    expect(screen.queryByTestId('builder-source-banner')).not.toBeInTheDocument()
  })

  it('in sync: info banner with a way back to the builder', async () => {
    vi.spyOn(measureApi, 'getBuilderSource').mockResolvedValue({
      artifactId: 9, artifactName: 'HbA1c', ownerUsername: 'alice', publishedAt: '2026-09-24T08:00:00',
      measureEditedSincePublish: false, builderChangedSincePublish: false,
    })
    render(<BuilderSourceBanner measureId={5} />)

    expect(await screen.findByText(/builderSource.builtIn/)).toBeInTheDocument()
    expect(screen.queryByText('builderSource.measureEdited')).not.toBeInTheDocument()
    expect(screen.queryByText('builderSource.builderChanged')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'builderSource.openInBuilder' }))
    expect(navigate).toHaveBeenCalledWith('/ecqm?artifact=9')
  })

  it('drift on either side is spelled out', async () => {
    vi.spyOn(measureApi, 'getBuilderSource').mockResolvedValue({
      artifactId: 9, artifactName: 'HbA1c', measureEditedSincePublish: true, builderChangedSincePublish: true,
    })
    render(<BuilderSourceBanner measureId={5} />)

    expect(await screen.findByText('builderSource.measureEdited')).toBeInTheDocument()
    expect(screen.getByText('builderSource.builderChanged')).toBeInTheDocument()
  })
})
