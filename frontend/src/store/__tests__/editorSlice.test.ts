import { describe, it, expect } from 'vitest'
import { configureStore } from '@reduxjs/toolkit'
import editorReducer, {
  setCqlContent,
  setElmJson,
  setErrors,
  setWarnings,
  clearEditor,
} from '../editorSlice'

function createStore() {
  return configureStore({ reducer: { editor: editorReducer } })
}

describe('editorSlice', () => {
  it('should have default CQL content on init', () => {
    const store = createStore()
    expect(store.getState().editor.cqlContent).toContain('DiabetesManagement')
  })

  it('setCqlContent should update content', () => {
    const store = createStore()
    store.dispatch(setCqlContent('library Test version "1.0"'))
    expect(store.getState().editor.cqlContent).toBe('library Test version "1.0"')
  })

  it('setElmJson should update elm', () => {
    const store = createStore()
    store.dispatch(setElmJson('{"library":{}}'))
    expect(store.getState().editor.elmJson).toBe('{"library":{}}')
  })

  it('setErrors and setWarnings should update arrays', () => {
    const store = createStore()
    const errors = [{ severity: 'Error', message: 'test error' }]
    const warnings = [{ severity: 'Warning', message: 'test warning' }]

    store.dispatch(setErrors(errors))
    store.dispatch(setWarnings(warnings))

    expect(store.getState().editor.errors).toEqual(errors)
    expect(store.getState().editor.warnings).toEqual(warnings)
  })

  it('clearEditor should reset all editor state', () => {
    const store = createStore()
    store.dispatch(setCqlContent('test'))
    store.dispatch(setElmJson('elm'))
    store.dispatch(setErrors([{ severity: 'Error', message: 'err' }]))

    store.dispatch(clearEditor())

    const state = store.getState().editor
    expect(state.cqlContent).toBe('')
    expect(state.elmJson).toBeNull()
    expect(state.errors).toEqual([])
    expect(state.warnings).toEqual([])
  })
})
