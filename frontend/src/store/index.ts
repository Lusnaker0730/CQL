import { configureStore } from '@reduxjs/toolkit'
import editorReducer from './editorSlice'
import executionReducer from './executionSlice'
import authReducer from './authSlice'

export const store = configureStore({
  reducer: {
    editor: editorReducer,
    execution: executionReducer,
    auth: authReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
