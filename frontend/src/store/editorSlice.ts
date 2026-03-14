import { createSlice, PayloadAction } from '@reduxjs/toolkit'

const MAX_HISTORY = 50

interface EditorState {
  cqlContent: string
  past: string[]
  future: string[]
  elmJson: string | null
  errors: CqlError[]
  warnings: CqlError[]
  isTranslating: boolean
  selectedLibraryId: string | null
  cursorPosition: { line: number; column: number }
  goToLine: number | null
}

interface CqlError {
  severity: string
  message: string
  startLine?: number
  startColumn?: number
  endLine?: number
  endColumn?: number
}

const DEFAULT_CQL = `library DiabetesManagement version '1.0.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1'

codesystem "LOINC": 'http://loinc.org'
codesystem "ConditionClinicalStatusCodes": 'http://terminology.hl7.org/CodeSystem/condition-clinical'

valueset "Diabetes": 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001'

code "HbA1c Code": '4548-4' from "LOINC" display 'Hemoglobin A1c'
code "Active": 'active' from "ConditionClinicalStatusCodes"

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2024-01-01T00:00:00.0, @2024-12-31T23:59:59.999]

context Patient

define "Has Diabetes":
  exists [Condition: "Diabetes"] C
    where C.clinicalStatus.coding contains "Active"

define "HbA1c Observations":
  [Observation: "HbA1c Code"]

define "Recent HbA1c":
  "HbA1c Observations" O
    where O.effective in "Measurement Period"
    sort by effective desc

define "Latest HbA1c Value":
  First("Recent HbA1c").value

define "Needs HbA1c Test":
  "Has Diabetes" and
  not exists "Recent HbA1c"
`

const initialState: EditorState = {
  cqlContent: DEFAULT_CQL,
  past: [],
  future: [],
  elmJson: null,
  errors: [],
  warnings: [],
  isTranslating: false,
  selectedLibraryId: null,
  cursorPosition: { line: 1, column: 1 },
  goToLine: null,
}

const editorSlice = createSlice({
  name: 'editor',
  initialState,
  reducers: {
    setCqlContent: (state, action: PayloadAction<string>) => {
      // No history tracking — used by Monaco typing
      state.cqlContent = action.payload
    },
    setCqlContentWithHistory: (state, action: PayloadAction<string>) => {
      // History tracked — used by builder insert/delete/edit
      if (state.cqlContent !== action.payload) {
        state.past.push(state.cqlContent)
        if (state.past.length > MAX_HISTORY) state.past.shift()
        state.future = []
      }
      state.cqlContent = action.payload
    },
    undo: (state) => {
      if (state.past.length === 0) return
      state.future.push(state.cqlContent)
      state.cqlContent = state.past.pop()!
    },
    redo: (state) => {
      if (state.future.length === 0) return
      state.past.push(state.cqlContent)
      state.cqlContent = state.future.pop()!
    },
    setElmJson: (state, action: PayloadAction<string | null>) => {
      state.elmJson = action.payload
    },
    setErrors: (state, action: PayloadAction<CqlError[]>) => {
      state.errors = action.payload
    },
    setWarnings: (state, action: PayloadAction<CqlError[]>) => {
      state.warnings = action.payload
    },
    setIsTranslating: (state, action: PayloadAction<boolean>) => {
      state.isTranslating = action.payload
    },
    setSelectedLibraryId: (state, action: PayloadAction<string | null>) => {
      state.selectedLibraryId = action.payload
    },
    setCursorPosition: (state, action: PayloadAction<{ line: number; column: number }>) => {
      state.cursorPosition = action.payload
    },
    setGoToLine: (state, action: PayloadAction<number | null>) => {
      state.goToLine = action.payload
    },
    clearEditor: (state) => {
      state.cqlContent = ''
      state.past = []
      state.future = []
      state.elmJson = null
      state.errors = []
      state.warnings = []
    },
  },
})

export const {
  setCqlContent,
  setCqlContentWithHistory,
  undo,
  redo,
  setElmJson,
  setErrors,
  setWarnings,
  setIsTranslating,
  setSelectedLibraryId,
  setCursorPosition,
  setGoToLine,
  clearEditor,
} = editorSlice.actions

export default editorSlice.reducer
