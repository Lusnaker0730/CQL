import React, { useMemo } from 'react'
import ReactDOM from 'react-dom/client'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider, CssBaseline } from '@mui/material'
import { BrowserRouter } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import './i18n'
import App from './App'
import { store } from './store'
import { createAppTheme } from './theme'
import { extractApiError } from './utils/errorUtils'
import { PreferencesProvider } from './contexts/PreferencesContext'
import { NotificationProvider } from './contexts/NotificationContext'
import { LibraryHistoryProvider } from './contexts/LibraryHistoryContext'
import { TerminologyDrawerProvider } from './contexts/TerminologyDrawerContext'
import GlobalNotification from './components/common/GlobalNotification'
import { usePreferences } from './hooks/usePreferences'
import { STALE_5M } from './constants/queryConstants'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: STALE_5M,
      retry: 1,
      refetchOnWindowFocus: false,
    },
    mutations: {
      onError: (error) => {
        console.error('[Mutation Error]', extractApiError(error), error)
      },
    },
  },
})

// eslint-disable-next-line react-refresh/only-export-components
function ThemedApp() {
  const { preferences } = usePreferences()
  const { i18n } = useTranslation()
  const theme = useMemo(
    () => createAppTheme(preferences.themeMode, i18n.language),
    [preferences.themeMode, i18n.language]
  )

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
            <TerminologyDrawerProvider>
              <LibraryHistoryProvider>
                <ThemedApp />
              </LibraryHistoryProvider>
            </TerminologyDrawerProvider>
          </NotificationProvider>
        </PreferencesProvider>
      </QueryClientProvider>
    </Provider>
  </React.StrictMode>
)
