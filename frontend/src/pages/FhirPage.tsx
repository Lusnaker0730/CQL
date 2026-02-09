import { useState } from 'react'
import { Box, Typography, Tabs, Tab } from '@mui/material'
import {
  Storage as FhirIcon,
  MenuBook as IgIcon,
} from '@mui/icons-material'
import FhirBrowser from '../components/fhir/FhirBrowser'
import ImplementationGuideBrowser from '../components/fhir/ImplementationGuideBrowser'

export default function FhirPage() {
  const [tabIndex, setTabIndex] = useState(0)

  return (
    <Box sx={{ height: 'calc(100vh - 120px)', p: 2, display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ mb: 0.5 }}>
          FHIR Resources
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
          Browse FHIR resources and Implementation Guide artifacts.
        </Typography>
      </Box>

      <Tabs
        value={tabIndex}
        onChange={(_, v) => setTabIndex(v)}
        sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}
      >
        <Tab icon={<FhirIcon />} iconPosition="start" label="FHIR Browser" />
        <Tab icon={<IgIcon />} iconPosition="start" label="TW Core IG" />
      </Tabs>

      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {tabIndex === 0 && <FhirBrowser />}
        {tabIndex === 1 && <ImplementationGuideBrowser />}
      </Box>
    </Box>
  )
}
