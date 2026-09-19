import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '../../../test/test-utils'
import PlatformValueSetDialog from '../PlatformValueSetDialog'

vi.setConfig({ testTimeout: 30_000 })

// test-utils does NOT initialize i18next; mock so queries match the i18n key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      const entries = Object.entries(opts ?? {}).filter(([k]) => k !== 'ns')
      return entries.length === 0 ? key : `${key}|${entries.map(([k, v]) => `${k}=${String(v)}`).join(',')}`
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const api = { get: vi.fn(), create: vi.fn(), update: vi.fn() }
vi.mock('../../../api', () => ({
  valueSetApi: {
    get: (id: number) => api.get(id),
    create: (b: unknown) => api.create(b),
    update: (id: number, b: unknown) => api.update(id, b),
  },
}))

const NHI = 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw'
const STORED = {
  id: 2, url: 'https://hospital.example.tw/fhir/ValueSet/HbA1cOrders', version: '1.1.0', name: 'HbA1cOrders',
  title: 'HbA1c order codes', status: 'draft', conceptCount: 1, origin: 'authored', ownerUsername: 'alice',
  concepts: [{ system: NHI, code: '09006C', display: 'HbA1c' }],
}

function type(label: string, value: string) {
  fireEvent.change(screen.getByRole('textbox', { name: new RegExp(`^${label.replace(/\./g, '\\.')}`) }), { target: { value } })
}

describe('PlatformValueSetDialog — PAT-230', () => {
  beforeEach(() => {
    Object.values(api).forEach((m) => m.mockReset())
    api.get.mockResolvedValue(STORED)
  })

  it('creates a value set from pasted codes, leaving the URL to the server when it is empty', async () => {
    api.create.mockResolvedValue({ ...STORED, id: 5 })
    const onClose = vi.fn()
    render(<PlatformValueSetDialog open valueSetId={null} onClose={onClose} />)

    type('platform.fields.name', ' HbA1cOrders ')
    type('platform.codes.defaultSystem', NHI)
    type('platform.codes.paste', '09006C, HbA1c\n09139C\n09006C, again')
    fireEvent.click(screen.getByRole('button', { name: 'platform.codes.add' }))

    const rows = within(screen.getByRole('table', { name: 'platform.codes.tableLabel' })).getAllByRole('row')
    expect(rows).toHaveLength(3) // header + 2: the repeated code was dropped
    expect(screen.getByText('platform.codes.heading|count=2,systems=1')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'actions.save' }))
    await waitFor(() => expect(api.create).toHaveBeenCalledTimes(1))
    expect(api.create.mock.calls[0][0]).toEqual({
      name: 'HbA1cOrders',
      title: undefined,
      description: undefined,
      publisher: undefined,
      url: undefined,
      version: '1.0.0',
      concepts: [{ system: NHI, code: '09006C', display: 'HbA1c' }, { system: NHI, code: '09139C' }],
    })
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('keeps unreadable lines in the box and says which ones', () => {
    render(<PlatformValueSetDialog open valueSetId={null} onClose={vi.fn()} />)

    type('platform.codes.paste', '09006C, HbA1c') // no default system → cannot be placed
    fireEvent.click(screen.getByRole('button', { name: 'platform.codes.add' }))

    expect(screen.getByText('platform.codes.skipped|lines=1')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /^platform\.codes\.paste/ })).toHaveValue('09006C, HbA1c')
    expect(screen.getByText('platform.codes.empty')).toBeInTheDocument()
  })

  it('edits a draft without sending url or version, and can remove a code', async () => {
    api.update.mockResolvedValue(STORED)
    render(<PlatformValueSetDialog open valueSetId={2} onClose={vi.fn()} />)

    expect(await screen.findByDisplayValue('HbA1cOrders')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /^platform\.fields\.url/ })).toBeDisabled()
    expect(screen.getByRole('textbox', { name: /^platform\.fields\.version/ })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'platform.codes.remove|code=09006C' }))
    fireEvent.click(screen.getByRole('button', { name: 'actions.save' }))

    await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1))
    const [id, body] = api.update.mock.calls[0] as [number, Record<string, unknown>]
    expect(id).toBe(2)
    expect(body.concepts).toEqual([])
    expect(body).not.toHaveProperty('url')
    expect(body).not.toHaveProperty('version')
  })

  it('shows an active version read-only, with the reason', async () => {
    api.get.mockResolvedValue({ ...STORED, status: 'active' })
    render(<PlatformValueSetDialog open valueSetId={2} onClose={vi.fn()} />)

    expect(await screen.findByText('platform.dialog.frozen|status=platform.status.active')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'actions.save' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'platform.codes.add' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /platform\.codes\.remove/ })).not.toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /^platform\.fields\.name/ })).toBeDisabled()
  })

  it('lists every validation problem the backend reports', async () => {
    api.create.mockRejectedValue(Object.assign(new Error('bad'), { name: 'AxiosError' }))
    const { AxiosError } = await import('axios')
    const err = new AxiosError('Request failed')
    err.response = { data: { message: 'Value set is not valid', details: ['name is required', 'url must be absolute'] } } as never
    api.create.mockRejectedValue(err)

    render(<PlatformValueSetDialog open valueSetId={null} onClose={vi.fn()} />)
    type('platform.fields.name', 'x')
    fireEvent.click(screen.getByRole('button', { name: 'actions.save' }))

    expect(await screen.findByText('Value set is not valid')).toBeInTheDocument()
    expect(screen.getByText('name is required')).toBeInTheDocument()
    expect(screen.getByText('url must be absolute')).toBeInTheDocument()
  })
})
