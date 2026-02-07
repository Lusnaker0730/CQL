import { describe, it, expect } from 'vitest'
import { configureStore } from '@reduxjs/toolkit'
import authReducer, { setCredentials, logout, setLoading } from '../authSlice'

function createStore() {
  return configureStore({ reducer: { auth: authReducer } })
}

describe('authSlice', () => {
  it('should have correct initial state', () => {
    const store = createStore()
    const state = store.getState().auth
    expect(state.user).toBeNull()
    expect(state.token).toBeNull()
    expect(state.isAuthenticated).toBe(false)
    expect(state.loading).toBe(false)
  })

  it('setCredentials should set user and token', () => {
    const store = createStore()
    store.dispatch(setCredentials({ token: 'jwt-token', username: 'testuser', role: 'USER' }))

    const state = store.getState().auth
    expect(state.token).toBe('jwt-token')
    expect(state.user).toEqual({ username: 'testuser', role: 'USER' })
    expect(state.isAuthenticated).toBe(true)
  })

  it('logout should clear user and token', () => {
    const store = createStore()
    store.dispatch(setCredentials({ token: 'jwt-token', username: 'testuser', role: 'USER' }))
    store.dispatch(logout())

    const state = store.getState().auth
    expect(state.token).toBeNull()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
  })

  it('setLoading should update loading state', () => {
    const store = createStore()
    store.dispatch(setLoading(true))
    expect(store.getState().auth.loading).toBe(true)

    store.dispatch(setLoading(false))
    expect(store.getState().auth.loading).toBe(false)
  })
})
