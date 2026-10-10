import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen, waitFor } from '../../../test/test-utils'
import TestCaseImportDialog from '../TestCaseImportDialog'
import { isTestCaseBundle, TEST_CASE_REPORT_PROFILE } from '../../../utils/testCaseBundles'

// test-utils does NOT initialize i18next; mock so queries match the key strings.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const batchImportTestCases = vi.fn()
const importTestCaseBundles = vi.fn()
vi.mock('../../../api', () => ({
  measureApi: {
    batchImportTestCases: (...args: unknown[]) => batchImportTestCases(...args),
    importTestCaseBundles: (...args: unknown[]) => importTestCaseBundles(...args),
  },
}))

const patientBundle = {
  resourceType: 'Bundle',
  type: 'collection',
  entry: [{ resource: { resourceType: 'Patient', id: 'p1', name: [{ family: 'Chen', given: ['Mei'] }] } }],
}

const testCaseBundle = {
  ...patientBundle,
  entry: [
    ...patientBundle.entry,
    {
      resource: {
        resourceType: 'MeasureReport',
        id: 'r1',
        meta: { profile: [TEST_CASE_REPORT_PROFILE] },
        status: 'complete',
        type: 'individual',
        group: [{ id: 'group-1', population: [{ code: { coding: [{ code: 'numerator' }] }, count: 1 }] }],
      },
    },
  ],
}

function chooseFile(file: File) {
  const input = document.querySelector('input[type="file"]') as HTMLInputElement
  fireEvent.change(input, { target: { files: [file] } })
}

// PAT-247 — MADiE-compatible import: a zip, or a bundle carrying a test-case MeasureReport, goes to
// the server untouched (the server turns the report into the expectation); plain bundles still take
// the client-side path, so the existing date-shift import keeps working.
describe('TestCaseImportDialog (PAT-247)', () => {
  beforeEach(() => {
    batchImportTestCases.mockReset()
    importTestCaseBundles.mockReset()
  })

  it('recognises a test-case bundle by profile or by the MADiE isTestCase modifier extension', () => {
    expect(isTestCaseBundle(testCaseBundle)).toBe(true)
    expect(isTestCaseBundle(patientBundle)).toBe(false)
    expect(isTestCaseBundle({
      resourceType: 'Bundle',
      entry: [{ resource: { resourceType: 'MeasureReport', modifierExtension: [{ url: 'http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-isTestCase', valueBoolean: true }] } }],
    })).toBe(true)
    expect(isTestCaseBundle(null)).toBe(false)
    expect(isTestCaseBundle({ resourceType: 'Patient' })).toBe(false)
  })

  it('sends a zip to the server as-is and shows the import warnings', async () => {
    importTestCaseBundles.mockResolvedValue({
      totalReceived: 2, successCount: 2, failureCount: 0, imported: [], errors: [],
      warnings: ["'Mei': expected values dropped — Unknown population group 'group-9'"],
    })
    render(<TestCaseImportDialog open onClose={vi.fn()} measureId={7} />)

    const zip = new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], 'HbA1c-test-cases.zip', { type: 'application/zip' })
    chooseFile(zip)

    expect(await screen.findByTestId('server-import-hint')).toBeInTheDocument()
    expect(screen.getByText('importDialog.importFile')).toBeInTheDocument()
    fireEvent.click(screen.getByText('importDialog.importFile'))

    await waitFor(() => expect(importTestCaseBundles).toHaveBeenCalledTimes(1))
    expect(importTestCaseBundles.mock.calls[0][0]).toBe(7)
    expect((importTestCaseBundles.mock.calls[0][1] as File).name).toBe('HbA1c-test-cases.zip')
    expect(batchImportTestCases).not.toHaveBeenCalled()
    expect(await screen.findByText('importDialog.warnings')).toBeInTheDocument()
    expect(screen.getByText(/Unknown population group/)).toBeInTheDocument()
  })

  it('routes a JSON bundle that carries a test-case MeasureReport to the server too', async () => {
    importTestCaseBundles.mockResolvedValue({ totalReceived: 1, successCount: 1, failureCount: 0, imported: [], errors: [], warnings: [] })
    render(<TestCaseImportDialog open onClose={vi.fn()} measureId={7} />)

    chooseFile(new File([JSON.stringify(testCaseBundle)], 'Mei.json', { type: 'application/json' }))

    expect(await screen.findByTestId('server-import-hint')).toBeInTheDocument()
    fireEvent.click(screen.getByText('importDialog.importFile'))
    await waitFor(() => expect(importTestCaseBundles).toHaveBeenCalledTimes(1))
    expect(batchImportTestCases).not.toHaveBeenCalled()
  })

  it('still parses a plain patient bundle on the client and imports it through batch-import', async () => {
    batchImportTestCases.mockResolvedValue({ totalReceived: 1, successCount: 1, failureCount: 0, imported: [], errors: [] })
    render(<TestCaseImportDialog open onClose={vi.fn()} measureId={7} />)

    chooseFile(new File([JSON.stringify(patientBundle)], 'plain.json', { type: 'application/json' }))

    expect(await screen.findByText('plain')).toBeInTheDocument()
    expect(screen.queryByTestId('server-import-hint')).not.toBeInTheDocument()
    fireEvent.click(screen.getByText('importDialog.importCount'))
    await waitFor(() => expect(batchImportTestCases).toHaveBeenCalledTimes(1))
    expect(importTestCaseBundles).not.toHaveBeenCalled()
    expect(batchImportTestCases.mock.calls[0][1]).toHaveLength(1)
  })
})
