import { createSlice, PayloadAction } from '@reduxjs/toolkit'

interface AuthState {
  user: { username: string; role: string; forcePasswordChange?: boolean; department?: string } | null
  token: string | null
  isAuthenticated: boolean
  loading: boolean
}

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp ? payload.exp * 1000 < Date.now() : false
  } catch {
    return true
  }
}

const token = localStorage.getItem('token')
const userStr = localStorage.getItem('user')
const user = userStr ? JSON.parse(userStr) : null
const isExpired = token ? isTokenExpired(token) : true

if (isExpired && token) {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
}

const initialState: AuthState = {
  user: isExpired ? null : user,
  token: isExpired ? null : token,
  isAuthenticated: !isExpired,
  loading: false,
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (
      state,
      action: PayloadAction<{ token: string; username: string; role: string; forcePasswordChange?: boolean; department?: string }>
    ) => {
      const { token, username, role, forcePasswordChange, department } = action.payload
      state.token = token
      state.user = { username, role, forcePasswordChange, department }
      state.isAuthenticated = true
      state.loading = false
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify({ username, role, forcePasswordChange, department }))
    },
    clearForcePasswordChange: (state) => {
      if (state.user) {
        state.user.forcePasswordChange = false
        localStorage.setItem('user', JSON.stringify(state.user))
      }
    },
    logout: (state) => {
      state.token = null
      state.user = null
      state.isAuthenticated = false
      state.loading = false
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload
    },
  },
})

export const { setCredentials, clearForcePasswordChange, logout, setLoading } = authSlice.actions
export default authSlice.reducer
