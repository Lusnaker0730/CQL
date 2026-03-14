import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import type { Artifact } from '../types/authoring'

interface ArtifactState {
  artifact: Artifact | null
  isDirty: boolean
  activeTab: number
  scrollToId: string | null
}

const initialState: ArtifactState = {
  artifact: null,
  isDirty: false,
  activeTab: 0,
  scrollToId: null,
}

const artifactSlice = createSlice({
  name: 'artifact',
  initialState,
  reducers: {
    loadArtifact(state, action: PayloadAction<Artifact>) {
      state.artifact = action.payload
      state.isDirty = false
      state.activeTab = 0
      state.scrollToId = null
    },
    updateArtifact(state, action: PayloadAction<Partial<Artifact>>) {
      if (state.artifact) {
        Object.assign(state.artifact, action.payload)
        state.isDirty = true
      }
    },
    markSaved(state) {
      state.isDirty = false
    },
    setActiveTab(state, action: PayloadAction<number>) {
      state.activeTab = action.payload
    },
    setScrollToId(state, action: PayloadAction<string | null>) {
      state.scrollToId = action.payload
    },
    clearArtifact(state) {
      state.artifact = null
      state.isDirty = false
      state.activeTab = 0
      state.scrollToId = null
    },
  },
})

export const {
  loadArtifact,
  updateArtifact,
  markSaved,
  setActiveTab,
  setScrollToId,
  clearArtifact,
} = artifactSlice.actions

export default artifactSlice.reducer
