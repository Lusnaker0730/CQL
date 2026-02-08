import React, { useMemo } from 'react'
import ReactDOM from 'react-dom/client'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider, CssBaseline } from '@mui/material'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { store } from './store'
import { createAppTheme } from './theme'
import { PreferencesProvider } from './contexts/PreferencesContext'
import { NotificationProvider } from './contexts/NotificationContext'
import { LibraryHistoryProvider } from './contexts/LibraryHistoryContext'
import GlobalNotification from './components/common/GlobalNotification'
import { usePreferences } from './hooks/usePreferences'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      retry: 1,
    },
  },
})

function ThemedApp() {
  const { preferences } = usePreferences()
  const theme = useMemo(() => createAppTheme(preferences.themeMode), [preferences.themeMode])

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <App />
      </BrowserRouter>
      <GlobalNotification />
    </ThemeProvider>
  )
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <PreferencesProvider>
          <NotificationProvider>
            <LibraryHistoryProvider>
              <ThemedApp />
            </LibraryHistoryProvider>
          </NotificationProvider>
        </PreferencesProvider>
      </QueryClientProvider>
    </Provider>
  </React.StrictMode>
)
