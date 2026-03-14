import { configureStore } from '@reduxjs/toolkit'
import editorReducer from './editorSlice'
import executionReducer from './executionSlice'
import authReducer from './authSlice'
import artifactReducer from './artifactSlice'

export const store = configureStore({
  reducer: {
    editor: editorReducer,
    execution: executionReducer,
    auth: authReducer,
    artifact: artifactReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
