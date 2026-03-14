import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useFilteredTwcoreCatalog } from '../useFilteredTwcoreCatalog'

const mockCatalog = [
  {
    name: 'Patient',
    resourceType: 'Patient',
    categories: [
      {
        name: 'Demographics',
        codes: [
          { code: 'gender', display: 'Gender', displayZh: '性別' },
          { code: 'birthDate', display: 'Birth Date', displayZh: '出生日期' },
        ],
      },
    ],
  },
  {
    name: 'Observation',
    resourceType: 'Observation',
    categories: [
      {
        name: 'Vital Signs',
        codes: [
          { code: '8867-4', display: 'Heart Rate', displayZh: '心率' },
          { code: '8310-5', display: 'Body Temperature', displayZh: '體溫' },
        ],
      },
    ],
  },
]

vi.mock('../useTwcoreCatalog', () => ({
  useTwcoreFullCatalog: vi.fn(() => ({
    data: mockCatalog,
    isLoading: false,
  })),
}))

describe('useFilteredTwcoreCatalog', () => {
  it('should return all entries when filter is empty', () => {
    const { result } = renderHook(() => useFilteredTwcoreCatalog(''))

    expect(result.current.filteredCatalog).toEqual(mockCatalog)
    expect(result.current.isLoading).toBe(false)
  })

  it('should return all entries when filter is whitespace only', () => {
    const { result } = renderHook(() => useFilteredTwcoreCatalog('   '))

    expect(result.current.filteredCatalog).toEqual(mockCatalog)
  })

  it('should filter by entry name (case-insensitive)', () => {
    const { result } = renderHook(() => useFilteredTwcoreCatalog('patient'))

    expect(result.current.filteredCatalog).toHaveLength(1)
    expect(result.current.filteredCatalog[0].name).toBe('Patient')
  })

  it('should filter by resourceType', () => {
    const { result } = renderHook(() => useFilteredTwcoreCatalog('observation'))

    expect(result.current.filteredCatalog).toHaveLength(1)
    expect(result.current.filteredCatalog[0].resourceType).toBe('Observation')
  })

  it('should filter by code display text', () => {
    const { result } = renderHook(() => useFilteredTwcoreCatalog('Heart Rate'))

    expect(result.current.filteredCatalog).toHaveLength(1)
    expect(result.current.filteredCatalog[0].name).toBe('Observation')
  })

  it('should filter by code value', () => {
    const { result } = renderHook(() => useFilteredTwcoreCatalog('8867'))

    expect(result.current.filteredCatalog).toHaveLength(1)
  })

  it('should filter by Chinese display text', () => {
    const { result } = renderHook(() => useFilteredTwcoreCatalog('心率'))

    expect(result.current.filteredCatalog).toHaveLength(1)
    expect(result.current.filteredCatalog[0].name).toBe('Observation')
  })

  it('should return empty array when nothing matches', () => {
    const { result } = renderHook(() => useFilteredTwcoreCatalog('nonexistent'))

    expect(result.current.filteredCatalog).toHaveLength(0)
  })
})
