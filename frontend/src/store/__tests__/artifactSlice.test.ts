import { describe, it, expect } from 'vitest'
import { configureStore } from '@reduxjs/toolkit'
import artifactReducer, {
  loadArtifact,
  updateArtifact,
  markSaved,
  setActiveTab,
  setScrollToId,
  clearArtifact,
} from '../artifactSlice'
import type { Artifact } from '../../types/authoring'

function createStore() {
  return configureStore({ reducer: { artifact: artifactReducer } })
}

const mockArtifact: Artifact = {
  id: 1,
  name: 'Test Artifact',
  version: '1.0',
  description: 'A test artifact',
  expTreeInclude: { id: 'root', name: 'And', conjunction: true, childInstances: [], path: '' },
  expTreeExclude: { id: 'root-ex', name: 'And', conjunction: true, childInstances: [], path: '' },
  subpopulations: [],
  recommendations: [],
  parameters: [],
  errorStatement: { ifThenClauses: [], elseClause: '' },
  baseElements: [],
  externalCqlList: [],
} as unknown as Artifact

describe('artifactSlice', () => {
  it('should have correct initial state', () => {
    const store = createStore()
    const state = store.getState().artifact
    expect(state.artifact).toBeNull()
    expect(state.isDirty).toBe(false)
    expect(state.activeTab).toBe(0)
    expect(state.scrollToId).toBeNull()
  })

  it('loadArtifact should set artifact and reset state', () => {
    const store = createStore()
    store.dispatch(setActiveTab(3))
    store.dispatch(loadArtifact(mockArtifact))

    const state = store.getState().artifact
    expect(state.artifact).toEqual(mockArtifact)
    expect(state.isDirty).toBe(false)
    expect(state.activeTab).toBe(0)
    expect(state.scrollToId).toBeNull()
  })

  it('updateArtifact should merge changes and mark dirty', () => {
    const store = createStore()
    store.dispatch(loadArtifact(mockArtifact))
    store.dispatch(updateArtifact({ name: 'Updated Name' }))

    const state = store.getState().artifact
    expect(state.artifact!.name).toBe('Updated Name')
    expect(state.isDirty).toBe(true)
  })

  it('updateArtifact should be no-op when artifact is null', () => {
    const store = createStore()
    store.dispatch(updateArtifact({ name: 'Updated Name' }))

    const state = store.getState().artifact
    expect(state.artifact).toBeNull()
    expect(state.isDirty).toBe(false)
  })

  it('markSaved should clear dirty flag', () => {
    const store = createStore()
    store.dispatch(loadArtifact(mockArtifact))
    store.dispatch(updateArtifact({ name: 'Changed' }))
    expect(store.getState().artifact.isDirty).toBe(true)

    store.dispatch(markSaved())
    expect(store.getState().artifact.isDirty).toBe(false)
  })

  it('setActiveTab should update tab index', () => {
    const store = createStore()
    store.dispatch(setActiveTab(5))
    expect(store.getState().artifact.activeTab).toBe(5)
  })

  it('setScrollToId should update scrollToId', () => {
    const store = createStore()
    store.dispatch(setScrollToId('element-123'))
    expect(store.getState().artifact.scrollToId).toBe('element-123')
  })

  it('clearArtifact should reset to initial state', () => {
    const store = createStore()
    store.dispatch(loadArtifact(mockArtifact))
    store.dispatch(updateArtifact({ name: 'Changed' }))
    store.dispatch(setActiveTab(3))

    store.dispatch(clearArtifact())
    const state = store.getState().artifact
    expect(state.artifact).toBeNull()
    expect(state.isDirty).toBe(false)
    expect(state.activeTab).toBe(0)
    expect(state.scrollToId).toBeNull()
  })
})
