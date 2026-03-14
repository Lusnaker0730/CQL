import { describe, it, expect } from 'vitest'

// Test the hook logic indirectly via store behavior since hooks need React context
import { configureStore } from '@reduxjs/toolkit'
import editorReducer, { setIsTranslating, setElmJson, setErrors, setWarnings } from '../../store/editorSlice'
import executionReducer, { setIsExecuting, setResults, setExecutionErrors } from '../../store/executionSlice'

function createStore() {
  return configureStore({
    reducer: { editor: editorReducer, execution: executionReducer },
  })
}

describe('useCql hook logic', () => {
  it('should set isTranslating when translation starts', () => {
    const store = createStore()
    store.dispatch(setIsTranslating(true))
    expect(store.getState().editor.isTranslating).toBe(true)
  })

  it('should update ELM and clear errors on successful translation', () => {
    const store = createStore()
    store.dispatch(setElmJson('{"library":{}}'))
    store.dispatch(setErrors([]))
    store.dispatch(setWarnings([]))
    store.dispatch(setIsTranslating(false))

    expect(store.getState().editor.elmJson).toBe('{"library":{}}')
    expect(store.getState().editor.errors).toEqual([])
    expect(store.getState().editor.isTranslating).toBe(false)
  })

  it('should set errors on failed translation', () => {
    const store = createStore()
    store.dispatch(setElmJson(null))
    store.dispatch(setErrors([{ severity: 'Error', message: 'Syntax error' }]))
    store.dispatch(setIsTranslating(false))

    expect(store.getState().editor.elmJson).toBeNull()
    expect(store.getState().editor.errors).toHaveLength(1)
  })

  it('should set execution results on success', () => {
    const store = createStore()
    const results = { IsAlive: { name: 'IsAlive', value: true, valueType: 'Boolean', displayValue: 'true' } }
    store.dispatch(setResults(results))
    store.dispatch(setIsExecuting(false))

    expect(store.getState().execution.results).toEqual(results)
    expect(store.getState().execution.isExecuting).toBe(false)
  })

  it('should set execution errors on failure', () => {
    const store = createStore()
    store.dispatch(setExecutionErrors(['Execution failed']))
    store.dispatch(setIsExecuting(false))

    expect(store.getState().execution.errors).toEqual(['Execution failed'])
  })
})
