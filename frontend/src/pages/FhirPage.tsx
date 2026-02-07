import { Box, Typography } from '@mui/material'
import FhirBrowser from '../components/fhir/FhirBrowser'

export default function FhirPage() {
  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2 }}>
      <Box sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ mb: 0.5 }}>
          FHIR Browser
        </Typography>
        <Box
          sx={{
            width: 48,
            height: 3,
            borderRadius: 2,
            background: 'linear-gradient(90deg, #0D7377, #14A3A8)',
            mb: 1,
          }}
        />
        <Typography variant="body2" color="text.secondary">
          Browse and explore FHIR resources on connected FHIR servers. Search for patients,
          conditions, observations, and other clinical data.
        </Typography>
      </Box>

      <Box sx={{ height: 'calc(100% - 90px)' }}>
        <FhirBrowser />
      </Box>
    </Box>
  )
}
