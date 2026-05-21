import { describe, it, expect, beforeEach, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'

const getStoredUsernameMock = vi.fn<[], string>()
vi.mock('../../utils/validation', async () => {
  const actual = await vi.importActual<typeof import('../../utils/validation')>(
    '../../utils/validation',
  )
  return {
    ...actual,
    getStoredUsername: () => getStoredUsernameMock(),
  }
})

import useFhirQueryHistory from '../useFhirQueryHistory'

const SCOPED_KEY = (user: string) => `fhir-query-history:${user}`
const LEGACY_KEY = 'fhir-query-history'

describe('useFhirQueryHistory — PAT-134 user-scoped storage (P0)', () => {
  beforeEach(() => {
    localStorage.clear()
    getStoredUsernameMock.mockReset()
  })

  it('writes new entries to a per-user key, not the legacy key', () => {
    getStoredUsernameMock.mockReturnValue('alice')
    const { result } = renderHook(() => useFhirQueryHistory())

    act(() => {
      result.current.addEntry('Patient', 'name=Smith', 'https://hapi.example')
    })

    const scoped = localStorage.getItem(SCOPED_KEY('alice'))
    expect(scoped).toBeTruthy()
    const parsed = JSON.parse(scoped!)
    expect(parsed).toHaveLength(1)
    expect(parsed[0]).toMatchObject({
      resourceType: 'Patient',
      params: 'name=Smith',
      fhirServer: 'https://hapi.example',
    })

    // Legacy key should be cleared on first save (migration cleanup)
    expect(localStorage.getItem(LEGACY_KEY)).toBeNull()
  })

  it('does not load another user\'s history (isolation between sessions)', () => {
    // User A populates a scoped key
    localStorage.setItem(
      SCOPED_KEY('alice'),
      JSON.stringify([
        { id: '1', resourceType: 'Patient', params: 'identifier=A123456789', fhirServer: 's', timestamp: 1, isFavorite: false },
      ]),
    )

    // User B mounts — should NOT see Alice's PHI-laden query.
    getStoredUsernameMock.mockReturnValue('bob')
    const { result } = renderHook(() => useFhirQueryHistory())
    expect(result.current.recent).toEqual([])
    expect(result.current.history).toEqual([])
  })

  it('does not auto-restore the legacy un-scoped key (could be cross-user)', () => {
    // Pre-existing legacy entry from before per-user scoping
    localStorage.setItem(
      LEGACY_KEY,
      JSON.stringify([
        { id: '1', resourceType: 'Observation', params: 'subject=Patient/x', fhirServer: 's', timestamp: 1, isFavorite: false },
      ]),
    )

    getStoredUsernameMock.mockReturnValue('alice')
    const { result } = renderHook(() => useFhirQueryHistory())
    // Confidentiality: don't replay the legacy bucket.
    expect(result.current.recent).toEqual([])
  })

  it('falls back to the unscoped key for anonymous sessions', () => {
    getStoredUsernameMock.mockReturnValue('anonymous')
    const { result } = renderHook(() => useFhirQueryHistory())

    act(() => {
      result.current.addEntry('Patient', 'name=Smith', 's')
    })

    expect(localStorage.getItem(LEGACY_KEY)).toBeTruthy()
    expect(localStorage.getItem(SCOPED_KEY('anonymous'))).toBeNull()
  })
})
