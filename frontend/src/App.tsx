import { lazy, Suspense, useEffect } from 'react'
import { Routes, Route } from 'react-router-dom'
import { Box } from '@mui/material'
import { useSelector, useDispatch } from 'react-redux'
import { useTranslation } from 'react-i18next'
import type { RootState } from './store'
import { updateToken } from './store/authSlice'
import Header from './components/layout/Header'
import Footer from './components/layout/Footer'
import LandingPage from './pages/LandingPage'
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
const AuditDashboardPage = lazy(() => import('./pages/AuditDashboardPage'))
const AuthoringPage = lazy(() => import('./pages/AuthoringPage'))
const EcqmPage = lazy(() => import('./pages/EcqmPage'))
const OktaCallbackPage = lazy(() => import('./pages/OktaCallbackPage'))
const LearnPage = lazy(() => import('./pages/LearnPage'))

export default function App() {
  const dispatch = useDispatch()
  const user = useSelector((state: RootState) => state.auth.user)
  const { t } = useTranslation()

  useEffect(() => {
    const handler = (e: Event) => {
      const token = (e as CustomEvent).detail?.token
      if (token) {
        dispatch(updateToken(token))
      }
    }
    window.addEventListener('token-refreshed', handler)
    return () => window.removeEventListener('token-refreshed', handler)
  }, [dispatch])

  return (
    <ErrorBoundary fallbackTitle={t('errors.applicationError')}>
      <ForcePasswordChangeDialog open={!!user?.forcePasswordChange} />
      <Routes>
        <Route path="/login" element={<LandingPage />} />
        <Route path="/learn" element={<Suspense fallback={<PageLoadingFallback />}><LearnPage /></Suspense>} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/auth/okta/callback" element={<Suspense fallback={<PageLoadingFallback />}><OktaCallbackPage /></Suspense>} />
        <Route
          path="/*"
          element={
            <ProtectedRoute>
              <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
                <Header />
                <Box
                  component="main"
                  sx={{
                    flex: 1,
                    overflow: 'auto',
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
                          <ErrorBoundary fallbackTitle={t('errors.editorError')}>
                            <EditorPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/cds"
                        element={
                          <ErrorBoundary fallbackTitle={t('errors.cdsHooksError')}>
                            <CdsPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/measures"
                        element={
                          <ErrorBoundary fallbackTitle={t('errors.measuresError')}>
                            <MeasuresPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/fhir"
                        element={
                          <ErrorBoundary fallbackTitle={t('errors.fhirBrowserError')}>
                            <FhirPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/terminology"
                        element={
                          <ErrorBoundary fallbackTitle={t('errors.terminologyError')}>
                            <TerminologyPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/authoring"
                        element={
                          <ErrorBoundary fallbackTitle={t('errors.cdsAuthoringError')}>
                            <AuthoringPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/ecqm"
                        element={
                          <ErrorBoundary fallbackTitle="eCQM Builder Error">
                            <EcqmPage />
                          </ErrorBoundary>
                        }
                      />
                      <Route
                        path="/admin/users"
                        element={
                          <AdminRoute>
                            <ErrorBoundary fallbackTitle={t('errors.adminError')}>
                              <AdminUsersPage />
                            </ErrorBoundary>
                          </AdminRoute>
                        }
                      />
                      <Route
                        path="/admin/audit"
                        element={
                          <AdminRoute>
                            <ErrorBoundary fallbackTitle={t('errors.auditDashboardError')}>
                              <AuditDashboardPage />
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
