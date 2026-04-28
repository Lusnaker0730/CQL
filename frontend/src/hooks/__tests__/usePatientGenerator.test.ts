import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'

const generateBatchMock = vi.fn()
const generateCustomPatientMock = vi.fn()
const downloadAsJsonMock = vi.fn()

vi.mock('../../utils/fhirPatientGenerator', () => ({
  generateBatch: (config: unknown) => generateBatchMock(config),
  generateCustomPatient: (config: unknown) => generateCustomPatientMock(config),
  downloadAsJson: (data: unknown, filename?: string) => downloadAsJsonMock(data, filename),
}))

import { usePatientGenerator } from '../usePatientGenerator'

describe('usePatientGenerator — PAT-135 (P3 coverage)', () => {
  beforeEach(() => {
    generateBatchMock.mockReset()
    generateCustomPatientMock.mockReset()
    downloadAsJsonMock.mockReset()
    vi.useFakeTimers()
  })

  it('generateBatchPatients flips isGenerating around the deferred call', () => {
    generateBatchMock.mockReturnValue([{ patient: { id: 'p1' } }])
    const { result } = renderHook(() => usePatientGenerator())

    act(() => {
      result.current.generateBatchPatients({
        numPatients: 1,
        numConditions: 0,
        numObservations: 0,
        numMedications: 0,
        numEncounters: 0,
        numAllergies: 0,
      })
    })
    expect(result.current.isGenerating).toBe(true)

    act(() => {
      vi.runAllTimers()
    })
    expect(result.current.isGenerating).toBe(false)
    expect(result.current.results).toHaveLength(1)
  })

  it('generateCustom honours numPatients (default 1) and stores results', () => {
    generateCustomPatientMock.mockReturnValue({ patient: { id: 'pX' } })
    const { result } = renderHook(() => usePatientGenerator())

    act(() => {
      result.current.generateCustom({
        selectedConditions: ['E11.9'],
        selectedObservations: [],
        selectedMedications: [],
        selectedAllergies: [],
        numEncounters: 1,
        numPatients: 3,
      })
    })
    act(() => {
      vi.runAllTimers()
    })

    expect(generateCustomPatientMock).toHaveBeenCalledTimes(3)
    expect(result.current.results).toHaveLength(3)
  })

  it('clearResults resets the array', () => {
    generateBatchMock.mockReturnValue([{ patient: { id: 'p1' } }])
    const { result } = renderHook(() => usePatientGenerator())

    act(() => {
      result.current.generateBatchPatients({
        numPatients: 1,
        numConditions: 0,
        numObservations: 0,
        numMedications: 0,
        numEncounters: 0,
        numAllergies: 0,
      })
    })
    act(() => vi.runAllTimers())
    expect(result.current.results).toHaveLength(1)

    act(() => {
      result.current.clearResults()
    })
    expect(result.current.results).toEqual([])
  })

  it('download is a no-op when there are no results', () => {
    const { result } = renderHook(() => usePatientGenerator())
    act(() => {
      result.current.download()
    })
    expect(downloadAsJsonMock).not.toHaveBeenCalled()
  })
})
