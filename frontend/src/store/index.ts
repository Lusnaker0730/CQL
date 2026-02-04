import { configureStore } from '@reduxjs/toolkit'
import editorReducer from './editorSlice'
import executionReducer from './executionSlice'

export const store = configureStore({
  reducer: {
    editor: editorReducer,
    execution: executionReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
