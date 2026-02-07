import { createSlice, PayloadAction } from '@reduxjs/toolkit'

interface AuthState {
  user: { username: string; role: string } | null
  token: string | null
  isAuthenticated: boolean
  loading: boolean
}

const token = localStorage.getItem('token')
const userStr = localStorage.getItem('user')
const user = userStr ? JSON.parse(userStr) : null

const initialState: AuthState = {
  user: user,
  token: token,
  isAuthenticated: !!token,
  loading: false,
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (
      state,
      action: PayloadAction<{ token: string; username: string; role: string }>
    ) => {
      const { token, username, role } = action.payload
      state.token = token
      state.user = { username, role }
      state.isAuthenticated = true
      state.loading = false
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify({ username, role }))
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

export const { setCredentials, logout, setLoading } = authSlice.actions
export default authSlice.reducer
