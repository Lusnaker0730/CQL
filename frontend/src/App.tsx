import { Routes, Route } from 'react-router-dom'
import { Box } from '@mui/material'
import Header from './components/layout/Header'
import Footer from './components/layout/Footer'
import EditorPage from './pages/EditorPage'
import CdsPage from './pages/CdsPage'
import MeasuresPage from './pages/MeasuresPage'
import FhirPage from './pages/FhirPage'

export default function App() {
  return (
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
        </Routes>
      </Box>
      <Footer />
    </Box>
  )
}
