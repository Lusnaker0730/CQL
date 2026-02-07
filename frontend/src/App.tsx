import { Routes, Route } from 'react-router-dom'
import { Box } from '@mui/material'
import Header from './components/layout/Header'
import Footer from './components/layout/Footer'
import EditorPage from './pages/EditorPage'
import CdsPage from './pages/CdsPage'
import MeasuresPage from './pages/MeasuresPage'
import FhirPage from './pages/FhirPage'
import TerminologyPage from './pages/TerminologyPage'
import LoginPage from './pages/LoginPage'
import ProtectedRoute from './components/auth/ProtectedRoute'

export default function App() {
  return (
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
                  background: 'linear-gradient(180deg, #EDF2F6 0%, #F4F7F9 50%, #F9FBFC 100%)',
                }}
              >
                <Routes>
                  <Route path="/" element={<EditorPage />} />
                  <Route path="/cds" element={<CdsPage />} />
                  <Route path="/measures" element={<MeasuresPage />} />
                  <Route path="/fhir" element={<FhirPage />} />
                  <Route path="/terminology" element={<TerminologyPage />} />
                </Routes>
              </Box>
              <Footer />
            </Box>
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}
