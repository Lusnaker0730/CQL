import { Box, Typography } from '@mui/material'
import FhirBrowser from '../components/fhir/FhirBrowser'

export default function FhirPage() {
  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2 }}>
      <Typography variant="h5" gutterBottom>
        FHIR Browser
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Browse and explore FHIR resources on connected FHIR servers. Search for patients,
        conditions, observations, and other clinical data.
      </Typography>

      <Box sx={{ height: 'calc(100% - 80px)' }}>
        <FhirBrowser />
      </Box>
    </Box>
  )
}
