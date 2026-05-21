import { describe, it, expect, vi } from 'vitest'
import { render, fireEvent } from '@testing-library/react'
import { useState } from 'react'
import SearchParamBuilder from '../SearchParamBuilder'

// PR #501 / PAT-161 pattern: SUT uses sub-path icon imports; no barrel mock
// is needed (and the Proxy version no longer works under vitest 4).

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

// Provide a tiny RESOURCE_SEARCH_PARAMS so the dropdown has options
vi.mock('../../../utils/fhirBrowserUtils', () => ({
  RESOURCE_SEARCH_PARAMS: {
    Patient: [
      { name: 'name', type: 'string', label: 'Name' },
      { name: 'identifier', type: 'token', label: 'Identifier' },
    ],
  },
}))

function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  const [mode, setMode] = useState<'structured' | 'raw'>('structured')
  return (
    <>
      <button onClick={() => setValue('name=Smith')} data-testid="parent-set-smith">
        parent-set-smith
      </button>
      <button onClick={() => setValue('identifier=A123456789')} data-testid="parent-set-id">
        parent-set-id
      </button>
      <span data-testid="parent-value">{value}</span>
      <SearchParamBuilder
        resourceType="Patient"
        value={value}
        onChange={setValue}
        mode={mode}
        onModeChange={setMode}
      />
    </>
  )
}

describe('SearchParamBuilder — PAT-134 parent value sync (P1)', () => {
  it('re-parses structured rows when parent prop value changes (history replay)', () => {
    const { container, getByTestId } = render(<Harness initial="" />)

    // No structured rows yet beyond the empty default
    let valueInputs = container.querySelectorAll('input[type="text"]')
    // initially: 1 row with empty name + empty value
    expect(valueInputs.length).toBeGreaterThan(0)

    // Parent sets value to 'name=Smith' (simulating history replay)
    fireEvent.click(getByTestId('parent-set-smith'))

    // Builder should now show a row with the value 'Smith' populated
    valueInputs = container.querySelectorAll('input[type="text"]')
    const valueWithSmith = Array.from(valueInputs).some(
      (i) => (i as HTMLInputElement).value === 'Smith',
    )
    expect(valueWithSmith).toBe(true)

    // Switch to a different value
    fireEvent.click(getByTestId('parent-set-id'))
    valueInputs = container.querySelectorAll('input[type="text"]')
    const valueWithId = Array.from(valueInputs).some(
      (i) => (i as HTMLInputElement).value === 'A123456789',
    )
    expect(valueWithId).toBe(true)
  })

  it('does not loop when local edits update parent value', () => {
    const { container, getByTestId } = render(<Harness initial="name=Foo" />)

    // Edit the value field locally
    const valueInputs = container.querySelectorAll('input[type="text"]')
    const fooInput = Array.from(valueInputs).find(
      (i) => (i as HTMLInputElement).value === 'Foo',
    ) as HTMLInputElement | undefined
    expect(fooInput).toBeDefined()

    fireEvent.change(fooInput!, { target: { value: 'Bar' } })

    // Parent value should reflect the edit (no infinite loop, no resetting)
    expect(getByTestId('parent-value').textContent).toBe('name=Bar')
  })
})
