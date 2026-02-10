import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { Box } from '@mui/material'
import Header from './components/layout/Header'
import Footer from './components/layout/Footer'
import LoginPage from './pages/LoginPage'
import ProtectedRoute from './components/auth/ProtectedRoute'
import ErrorBoundary from './components/common/ErrorBoundary'
import PageLoadingFallback from './components/common/PageLoadingFallback'

const EditorPage = lazy(() => import('./pages/EditorPage'))
const CdsPage = lazy(() => import('./pages/CdsPage'))
const MeasuresPage = lazy(() => import('./pages/MeasuresPage'))
const FhirPage = lazy(() => import('./pages/FhirPage'))
const TerminologyPage = lazy(() => import('./pages/TerminologyPage'))

export default function App() {
  return (
    <ErrorBoundary fallbackTitle="Application Error">
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/*"
          element={
            <ProtectedRoute>
              <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
                <Header />
                <Box
                  component="main"
                  sx={{
                    flexGrow: 1,
                    background: (theme) =>
                      theme.palette.mode === 'dark'
                        ? 'linear-gradient(180deg, #1a1a1a 0%, #121212 100%)'
                        : 'linear-gradient(180deg, #EDF2F6 0%, #F4F7F9 50%, #F9FBFC 100%)',
                  }}
                >
                  <Suspense fallback={<PageLoadingFallback />}>
                    <Routes>
                      <Route
                        path="/"
                        element={
                          <ErrorBoundary fallbackTitle="Editor Error">
                            <EditorPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/cds"
                        element={
                          <ErrorBoundary fallbackTitle="CDS Hooks Error">
                            <CdsPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/measures"
                        element={
                          <ErrorBoundary fallbackTitle="Measures Error">
                            <MeasuresPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/fhir"
                        element={
                          <ErrorBoundary fallbackTitle="FHIR Browser Error">
                            <FhirPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/terminology"
                        element={
                          <ErrorBoundary fallbackTitle="Terminology Error">
                            <TerminologyPage />
                          </ErrorBoundary>
                        }
                      />
                    </Routes>
                  </Suspense>
                </Box>
                <Footer />
              </Box>
            </ProtectedRoute>
          }
        />
      </Routes>
    </ErrorBoundary>
  )
}
