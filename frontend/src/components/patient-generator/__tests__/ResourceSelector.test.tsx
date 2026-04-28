import { describe, it, expect, vi } from 'vitest'
import { render, fireEvent } from '@testing-library/react'
import { useState } from 'react'
import ResourceSelector from '../ResourceSelector'

vi.mock('@mui/icons-material', () => {
  const Stub = () => null
  return new Proxy(
    { default: Stub },
    { get: (_t, prop) => (prop === '__esModule' ? true : Stub) },
  )
})

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (typeof opts === 'string') return opts
      if (!opts) return key
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

interface Item {
  code: string
  display: string
}

const CATEGORIES = {
  diabetes: {
    name: 'Diabetes',
    items: [
      { code: 'E11.9', display: 'Type 2 diabetes mellitus without complications' },
      { code: 'E10.9', display: 'Type 1 diabetes mellitus without complications' },
    ],
  },
  cardiovascular: {
    name: 'Cardiovascular',
    items: [
      { code: 'I10', display: 'Essential (primary) hypertension' },
      { code: 'I50.9', display: 'Heart failure, unspecified' },
    ],
  },
}

function Harness() {
  const [selected, setSelected] = useState<string[]>([])
  return (
    <>
      <div data-testid="selected-codes">{selected.join(',')}</div>
      <ResourceSelector<Item>
        title="Conditions"
        categories={CATEGORIES}
        selectedCodes={selected}
        onSelectionChange={setSelected}
      />
    </>
  )
}

describe('ResourceSelector — PAT-135 selectAll respects search filter (P1)', () => {
  it('Select All on a search filter only adds the visible items', () => {
    const { getByPlaceholderText, getByText, getByTestId } = render(<Harness />)

    // Type a search query that only matches diabetes
    const search = getByPlaceholderText('custom.searchPlaceholder')
    fireEvent.change(search, { target: { value: 'diabetes' } })

    // Press Select All — should pick only the 2 diabetes codes, not all 4.
    fireEvent.click(getByText('custom.selectAll'))

    const selected = getByTestId('selected-codes').textContent ?? ''
    const codes = selected.split(',').filter(Boolean)
    expect(codes.sort()).toEqual(['E10.9', 'E11.9'])
    expect(codes).not.toContain('I10')
    expect(codes).not.toContain('I50.9')
  })

  it('Deselect All on a search filter only clears visible items, leaving other categories selected', () => {
    const { getByPlaceholderText, getByText, getByTestId } = render(<Harness />)

    // First: select everything (no filter)
    fireEvent.click(getByText('custom.selectAll'))
    expect(getByTestId('selected-codes').textContent).toContain('E11.9')
    expect(getByTestId('selected-codes').textContent).toContain('I10')

    // Now filter to diabetes and deselect all visible.
    const search = getByPlaceholderText('custom.searchPlaceholder')
    fireEvent.change(search, { target: { value: 'diabetes' } })
    fireEvent.click(getByText('custom.deselectAll'))

    // Cardiovascular codes should remain.
    const remaining = (getByTestId('selected-codes').textContent ?? '').split(',').filter(Boolean)
    expect(remaining).toContain('I10')
    expect(remaining).toContain('I50.9')
    expect(remaining).not.toContain('E11.9')
    expect(remaining).not.toContain('E10.9')
  })

  it('Select All without a search filter still grabs everything', () => {
    const { getByText, getByTestId } = render(<Harness />)

    fireEvent.click(getByText('custom.selectAll'))
    const codes = (getByTestId('selected-codes').textContent ?? '').split(',').filter(Boolean)
    expect(codes.sort()).toEqual(['E10.9', 'E11.9', 'I10', 'I50.9'])
  })
})
