import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '../../../test/test-utils'
import PlatformValueSetTab from '../PlatformValueSetTab'
import type { PlatformValueSet } from '../../../types'

vi.setConfig({ testTimeout: 30_000 })

// test-utils does NOT initialize i18next; mock so queries match the i18n key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    // key, plus the interpolation values so assertions can see what was passed: "key|a=1,b=2"
    t: (key: string, opts?: Record<string, unknown>) => {
      const entries = Object.entries(opts ?? {}).filter(([k]) => k !== 'ns')
      return entries.length === 0 ? key : `${key}|${entries.map(([k, v]) => `${k}=${String(v)}`).join(',')}`
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const api = {
  list: vi.fn(),
  get: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  createVersion: vi.fn(),
  activate: vi.fn(),
  retire: vi.fn(),
  remove: vi.fn(),
  exportFhir: vi.fn(),
  importFhir: vi.fn(),
}
vi.mock('../../../api', () => ({
  valueSetApi: {
    list: (s?: string) => api.list(s),
    get: (id: number) => api.get(id),
    create: (b: unknown) => api.create(b),
    update: (id: number, b: unknown) => api.update(id, b),
    createVersion: (id: number, v: string) => api.createVersion(id, v),
    activate: (id: number) => api.activate(id),
    retire: (id: number) => api.retire(id),
    remove: (id: number) => api.remove(id),
    exportFhir: (id: number) => api.exportFhir(id),
    importFhir: (r: unknown) => api.importFhir(r),
  },
}))

const copyMock = vi.fn()
vi.mock('../../../hooks/useCopyToClipboard', () => ({ useCopyToClipboard: () => copyMock }))

const NHI = 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw'
const URL_ = 'https://hospital.example.tw/fhir/ValueSet/HbA1cOrders'
const base = { url: URL_, name: 'HbA1cOrders', title: 'HbA1c order codes', origin: 'authored', ownerUsername: 'alice' } as const
const ACTIVE: PlatformValueSet = { ...base, id: 1, version: '1.0.0', status: 'active', conceptCount: 2 }
const DRAFT: PlatformValueSet = { ...base, id: 2, version: '1.1.0', status: 'draft', conceptCount: 3 }

function openMenu(version: string) {
  fireEvent.click(screen.getByRole('button', { name: `platform.actionsFor|name=HbA1c order codes,version=${version}` }))
}

describe('PlatformValueSetTab — PAT-230', () => {
  beforeEach(() => {
    Object.values(api).forEach((m) => m.mockReset())
    copyMock.mockReset()
    api.list.mockResolvedValue([DRAFT, ACTIVE])
    api.get.mockResolvedValue({ ...DRAFT, concepts: [{ system: NHI, code: '09006C', display: 'HbA1c' }] })
  })

  it('lists one row per version with its status', async () => {
    render(<PlatformValueSetTab />)
    const table = await screen.findByRole('table', { name: 'platform.tableLabel' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('platform.status.draft')).toBeInTheDocument()
    expect(within(rows[2]).getByText('platform.status.active')).toBeInTheDocument()
    expect(within(rows[2]).getByText(URL_)).toBeInTheDocument()
  })

  it('offers only what the status allows: a draft can be activated or deleted, an active version only retired', async () => {
    render(<PlatformValueSetTab />)
    await screen.findByRole('table', { name: 'platform.tableLabel' })

    openMenu('1.1.0')
    expect(screen.getByRole('menuitem', { name: 'platform.actions.activate' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'platform.actions.delete' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'platform.actions.retire' })).not.toBeInTheDocument()
    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument())

    openMenu('1.0.0')
    expect(screen.getByRole('menuitem', { name: 'platform.actions.retire' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'platform.actions.activate' })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'platform.actions.delete' })).not.toBeInTheDocument()
  })

  it('activates a draft, and shows why when the backend refuses', async () => {
    api.activate.mockRejectedValue(Object.assign(new Error('x'), {
      isAxiosError: true,
      response: { data: { message: 'An empty value set cannot be activated' } },
    }))
    render(<PlatformValueSetTab />)
    await screen.findByRole('table', { name: 'platform.tableLabel' })

    openMenu('1.1.0')
    fireEvent.click(screen.getByRole('menuitem', { name: 'platform.actions.activate' }))

    await waitFor(() => expect(api.activate).toHaveBeenCalledWith(2))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })

  it('copies the CQL declaration, pinned or not', async () => {
    render(<PlatformValueSetTab />)
    await screen.findByRole('table', { name: 'platform.tableLabel' })

    openMenu('1.0.0')
    fireEvent.click(screen.getByRole('menuitem', { name: 'platform.actions.copyCqlPinned' }))
    expect(copyMock).toHaveBeenCalledWith(`valueset "HbA1c order codes": '${URL_}' version '1.0.0'`)
  })

  it('creates a new version with a suggested number and opens the draft', async () => {
    api.createVersion.mockResolvedValue({ ...DRAFT, id: 3, version: '1.0.1' })
    render(<PlatformValueSetTab />)
    await screen.findByRole('table', { name: 'platform.tableLabel' })

    openMenu('1.0.0')
    fireEvent.click(screen.getByRole('menuitem', { name: 'platform.actions.newVersion' }))
    const field = await screen.findByRole('textbox', { name: 'platform.fields.version' })
    expect(field).toHaveValue('1.0.1')
    fireEvent.click(screen.getByRole('button', { name: 'platform.newVersion.confirm' }))

    await waitFor(() => expect(api.createVersion).toHaveBeenCalledWith(1, '1.0.1'))
    await waitFor(() => expect(api.get).toHaveBeenCalledWith(3))
  })

  it('imports a FHIR ValueSet file and says it arrived as a draft', async () => {
    api.importFhir.mockResolvedValue({ ...DRAFT, id: 9, conceptCount: 12 })
    render(<PlatformValueSetTab />)
    await screen.findByRole('table', { name: 'platform.tableLabel' })

    const resource = { resourceType: 'ValueSet', url: URL_ }
    const file = new File([JSON.stringify(resource)], 'vs.json', { type: 'application/json' })
    Object.defineProperty(file, 'text', { value: () => Promise.resolve(JSON.stringify(resource)) })
    fireEvent.change(screen.getByLabelText('platform.importFhir', { selector: 'input' }), { target: { files: [file] } })

    await waitFor(() => expect(api.importFhir).toHaveBeenCalledWith(resource))
    expect(await screen.findByText(/platform\.imported\|name=HbA1c order codes,count=12/)).toBeInTheDocument()
  })

  it('refuses a file that is not JSON without calling the backend', async () => {
    render(<PlatformValueSetTab />)
    await screen.findByRole('table', { name: 'platform.tableLabel' })

    const file = new File(['<ValueSet/>'], 'vs.xml', { type: 'text/xml' })
    Object.defineProperty(file, 'text', { value: () => Promise.resolve('<ValueSet/>') })
    fireEvent.change(screen.getByLabelText('platform.importFhir', { selector: 'input' }), { target: { files: [file] } })

    expect(await screen.findByText('platform.notJson')).toBeInTheDocument()
    expect(api.importFhir).not.toHaveBeenCalled()
  })
})
