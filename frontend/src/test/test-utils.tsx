import React, { type ReactElement } from 'react'
import { render, type RenderOptions } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { HelmetProvider } from 'react-helmet-async'
import editorReducer from '../store/editorSlice'
import executionReducer from '../store/executionSlice'
import authReducer from '../store/authSlice'
import { ThemeProvider } from '@mui/material/styles'
import { theme } from '../theme'
import { PreferencesProvider } from '../contexts/PreferencesContext'
import { NotificationProvider } from '../contexts/NotificationContext'

function createTestStore(preloadedState?: Record<string, unknown>) {
  return configureStore({
    reducer: {
      editor: editorReducer,
      execution: executionReducer,
      auth: authReducer,
    },
    preloadedState: preloadedState as Record<string, unknown>,
  })
}

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  preloadedState?: Record<string, unknown>
  route?: string
}

function customRender(
  ui: ReactElement,
  {
    preloadedState,
    route = '/',
    ...renderOptions
  }: CustomRenderOptions = {}
) {
  const store = createTestStore(preloadedState)
  const queryClient = createTestQueryClient()

  function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <HelmetProvider>
        <Provider store={store}>
          <QueryClientProvider client={queryClient}>
            <PreferencesProvider>
              <NotificationProvider>
                <ThemeProvider theme={theme}>
                  <MemoryRouter initialEntries={[route]}>
                    {children}
                  </MemoryRouter>
                </ThemeProvider>
              </NotificationProvider>
            </PreferencesProvider>
          </QueryClientProvider>
        </Provider>
      </HelmetProvider>
    )
  }

  return { ...render(ui, { wrapper: Wrapper, ...renderOptions }), store, queryClient }
}

// eslint-disable-next-line react-refresh/only-export-components
export * from '@testing-library/react'
export { customRender as render }
