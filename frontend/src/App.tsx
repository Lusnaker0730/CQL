import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { Box } from '@mui/material'
import { useSelector } from 'react-redux'
import type { RootState } from './store'
import Header from './components/layout/Header'
import Footer from './components/layout/Footer'
import LoginPage from './pages/LoginPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import ProtectedRoute from './components/auth/ProtectedRoute'
import AdminRoute from './components/auth/AdminRoute'
import ForcePasswordChangeDialog from './components/auth/ForcePasswordChangeDialog'
import ErrorBoundary from './components/common/ErrorBoundary'
import PageLoadingFallback from './components/common/PageLoadingFallback'

const EditorPage = lazy(() => import('./pages/EditorPage'))
const CdsPage = lazy(() => import('./pages/CdsPage'))
const MeasuresPage = lazy(() => import('./pages/MeasuresPage'))
const FhirPage = lazy(() => import('./pages/FhirPage'))
const TerminologyPage = lazy(() => import('./pages/TerminologyPage'))
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage'))

export default function App() {
  const user = useSelector((state: RootState) => state.auth.user)

  return (
    <ErrorBoundary fallbackTitle="Application Error">
      <ForcePasswordChangeDialog open={!!user?.forcePasswordChange} />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
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
                      <Route
                        path="/admin/users"
                        element={
                          <AdminRoute>
                            <ErrorBoundary fallbackTitle="Admin Error">
                              <AdminUsersPage />
                            </ErrorBoundary>
                          </AdminRoute>
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
