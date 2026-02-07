import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import type { DebugTrace } from '../types'

interface ExecutionResult {
  name: string
  value: unknown
  valueType: string
  displayValue: string
}

interface ExecutionState {
  patientId: string
  fhirServerUrl: string
  isExecuting: boolean
  results: Record<string, ExecutionResult>
  errors: string[]
  executionTimeMs: number | null
  debugTrace: DebugTrace | null
}

const initialState: ExecutionState = {
  patientId: '',
  fhirServerUrl: 'http://hapi.fhir.org/baseR4',
  isExecuting: false,
  results: {},
  errors: [],
  executionTimeMs: null,
  debugTrace: null,
}

const executionSlice = createSlice({
  name: 'execution',
  initialState,
  reducers: {
    setPatientId: (state, action: PayloadAction<string>) => {
      state.patientId = action.payload
    },
    setFhirServerUrl: (state, action: PayloadAction<string>) => {
      state.fhirServerUrl = action.payload
    },
    setIsExecuting: (state, action: PayloadAction<boolean>) => {
      state.isExecuting = action.payload
    },
    setResults: (state, action: PayloadAction<Record<string, ExecutionResult>>) => {
      state.results = action.payload
    },
    setExecutionErrors: (state, action: PayloadAction<string[]>) => {
      state.errors = action.payload
    },
    setExecutionTimeMs: (state, action: PayloadAction<number | null>) => {
      state.executionTimeMs = action.payload
    },
    setDebugTrace: (state, action: PayloadAction<DebugTrace | null>) => {
      state.debugTrace = action.payload
    },
    clearResults: (state) => {
      state.results = {}
      state.errors = []
      state.executionTimeMs = null
      state.debugTrace = null
    },
  },
})

export const {
  setPatientId,
  setFhirServerUrl,
  setIsExecuting,
  setResults,
  setExecutionErrors,
  setExecutionTimeMs,
  setDebugTrace,
  clearResults,
} = executionSlice.actions

export default executionSlice.reducer
