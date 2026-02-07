import { describe, it, expect } from 'vitest'
import { configureStore } from '@reduxjs/toolkit'
import executionReducer, {
  setPatientId,
  setFhirServerUrl,
  setResults,
  setExecutionErrors,
  setExecutionTimeMs,
  clearResults,
} from '../executionSlice'

function createStore() {
  return configureStore({ reducer: { execution: executionReducer } })
}

describe('executionSlice', () => {
  it('should have correct initial state', () => {
    const store = createStore()
    const state = store.getState().execution
    expect(state.patientId).toBe('')
    expect(state.fhirServerUrl).toContain('hapi.fhir.org')
    expect(state.isExecuting).toBe(false)
    expect(state.results).toEqual({})
    expect(state.errors).toEqual([])
  })

  it('should set patient id and fhir server url', () => {
    const store = createStore()
    store.dispatch(setPatientId('patient-123'))
    store.dispatch(setFhirServerUrl('http://custom/fhir'))

    expect(store.getState().execution.patientId).toBe('patient-123')
    expect(store.getState().execution.fhirServerUrl).toBe('http://custom/fhir')
  })

  it('should set results and execution time', () => {
    const store = createStore()
    const results = {
      'IsAlive': { name: 'IsAlive', value: true, valueType: 'Boolean', displayValue: 'true' },
    }
    store.dispatch(setResults(results))
    store.dispatch(setExecutionTimeMs(150))

    expect(store.getState().execution.results).toEqual(results)
    expect(store.getState().execution.executionTimeMs).toBe(150)
  })

  it('clearResults should reset results and errors', () => {
    const store = createStore()
    store.dispatch(setResults({ test: { name: 'test', value: 1, valueType: 'Integer', displayValue: '1' } }))
    store.dispatch(setExecutionErrors(['some error']))
    store.dispatch(setExecutionTimeMs(100))

    store.dispatch(clearResults())

    const state = store.getState().execution
    expect(state.results).toEqual({})
    expect(state.errors).toEqual([])
    expect(state.executionTimeMs).toBeNull()
  })
})
