import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import { safeLocalStorage } from '../utils/safeStorage'

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

// PAT-149: every storage access goes through safeLocalStorage so Safari Private
// mode (quota=0 → setItem throws) or storage-disabled browsers can still load
// the app + complete reducers. The trade-off is "user has to log in again on
// next refresh" instead of "module-load throw → white screen."
const token = safeLocalStorage.getItem('token')
const userStr = safeLocalStorage.getItem('user')
let user: { username: string; role: string; forcePasswordChange?: boolean; department?: string } | null = null
try {
  user = userStr ? JSON.parse(userStr) : null
} catch {
  user = null
}
const isExpired = token ? isTokenExpired(token) : true

if (isExpired && token) {
  safeLocalStorage.removeItem('token')
  safeLocalStorage.removeItem('user')
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
      safeLocalStorage.setItem('token', token)
      safeLocalStorage.setItem('user', JSON.stringify({ username, role, forcePasswordChange, department }))
    },
    clearForcePasswordChange: (state) => {
      if (state.user) {
        state.user.forcePasswordChange = false
        safeLocalStorage.setItem('user', JSON.stringify(state.user))
      }
    },
    updateToken: (state, action: PayloadAction<string>) => {
      state.token = action.payload
      safeLocalStorage.setItem('token', action.payload)
    },
    logout: (state) => {
      state.token = null
      state.user = null
      state.isAuthenticated = false
      state.loading = false
      safeLocalStorage.removeItem('token')
      safeLocalStorage.removeItem('user')
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload
    },
  },
})

export const { setCredentials, clearForcePasswordChange, updateToken, logout, setLoading } = authSlice.actions
export default authSlice.reducer
