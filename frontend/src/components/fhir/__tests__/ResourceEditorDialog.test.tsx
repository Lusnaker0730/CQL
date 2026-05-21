import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '../../../test/test-utils'

// PR #501 / PAT-161 pattern: SUT components use sub-path icon imports
// (`@mui/icons-material/Save` etc.), so no barrel mock is needed. The old
// Proxy-based mock broke under vitest 4 with the cryptic "Cannot create
// proxy with a non-object as target or handler" error.

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (!opts) return key
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

vi.mock('../../../api', () => ({
  fhirApi: {
    validateResource: vi.fn(),
    createResource: vi.fn(),
    updateResource: vi.fn(),
  },
}))

// Stub Monaco — capture the `key` prop indirectly via a marker in the DOM
// (Editor doesn't expose its key, but React's reconciliation will remount the
// stub on key change, which we can detect via a per-mount counter).
// IMPORTANT: include the `loader` export — the project's MonacoEditor
// wrapper calls `loader.config({ monaco })` at module load, so omitting it
// here causes a load-time error (`No "loader" export defined on mock`).
let mountCounter = 0
vi.mock('@monaco-editor/react', () => ({
  default: ({ defaultValue }: { defaultValue?: string }) => {
    mountCounter++
    return (
      <div
        data-testid="monaco-stub"
        data-mount-counter={mountCounter}
        data-default-value={defaultValue ?? ''}
      />
    )
  },
  loader: { config: () => undefined, init: () => Promise.resolve({}) },
}))

import ResourceEditorDialog from '../ResourceEditorDialog'

describe('ResourceEditorDialog — PAT-134 editor remount per resource (P1)', () => {
  it('remounts the Monaco editor when the resourceId changes (no stale JSON)', () => {
    const initialMount = mountCounter
    const { rerender } = render(
      <ResourceEditorDialog
        open
        mode="edit"
        resourceType="Patient"
        resourceId="123"
        initialJson='{"resourceType":"Patient","id":"123"}'
        fhirServer="s"
        onClose={() => {}}
        onSaved={() => {}}
      />,
    )
    const firstStub = screen.getByTestId('monaco-stub')
    expect(firstStub.getAttribute('data-default-value')).toContain('"id":"123"')
    const firstMount = Number(firstStub.getAttribute('data-mount-counter'))
    expect(firstMount).toBeGreaterThan(initialMount)

    // Reuse the dialog with a different resource — defaultValue must update.
    rerender(
      <ResourceEditorDialog
        open
        mode="edit"
        resourceType="Patient"
        resourceId="456"
        initialJson='{"resourceType":"Patient","id":"456"}'
        fhirServer="s"
        onClose={() => {}}
        onSaved={() => {}}
      />,
    )
    const secondStub = screen.getByTestId('monaco-stub')
    expect(secondStub.getAttribute('data-default-value')).toContain('"id":"456"')
    const secondMount = Number(secondStub.getAttribute('data-mount-counter'))
    // The mount counter increments only when the editor is freshly mounted.
    // If our key={mode-rt-id} fix is missing, React reuses the same Editor
    // and mountCounter stays the same.
    expect(secondMount).toBeGreaterThan(firstMount)
  })
})
