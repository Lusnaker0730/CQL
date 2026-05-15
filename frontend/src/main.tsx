import React, { useMemo } from 'react'
import ReactDOM from 'react-dom/client'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider, CssBaseline } from '@mui/material'
import { BrowserRouter } from 'react-router-dom'
import { HelmetProvider } from 'react-helmet-async'
import { useTranslation } from 'react-i18next'
import { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'
import './i18n'
import App from './App'

// Use the bundled monaco-editor package instead of the default jsdelivr CDN.
// PAT-157 removed cdn.jsdelivr.net from the script-src CSP allow-list; without
// this call @monaco-editor/react falls back to its CDN loader, the script tag
// is blocked by CSP, and every Monaco-backed editor (CQL editor, library editor,
// CDS sandbox, FHIR resource editor, CQL preview boxes) fails to initialize.
// Bundling Monaco also means we ship a known version (no surprise upgrades from
// jsdelivr's latest) and works offline.
loader.config({ monaco })
import { store } from './store'
import { createAppTheme } from './theme'
import { extractApiError } from './utils/errorUtils'
import { PreferencesProvider } from './contexts/PreferencesContext'
import { NotificationProvider } from './contexts/NotificationContext'
import { EhrOutageProvider } from './contexts/EhrOutageContext'
import { LibraryHistoryProvider } from './contexts/LibraryHistoryContext'
import { TerminologyDrawerProvider } from './contexts/TerminologyDrawerContext'
import GlobalNotification from './components/common/GlobalNotification'
import VersionCheckProvider from './components/common/VersionCheckProvider'
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
    <HelmetProvider>
      <Provider store={store}>
        <QueryClientProvider client={queryClient}>
          <PreferencesProvider>
            <NotificationProvider>
              <EhrOutageProvider>
                <TerminologyDrawerProvider>
                  <LibraryHistoryProvider>
                    <VersionCheckProvider>
                      <ThemedApp />
                    </VersionCheckProvider>
                  </LibraryHistoryProvider>
                </TerminologyDrawerProvider>
              </EhrOutageProvider>
            </NotificationProvider>
          </PreferencesProvider>
        </QueryClientProvider>
      </Provider>
    </HelmetProvider>
  </React.StrictMode>
)
