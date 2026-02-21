import { useState } from 'react'
import { Box, Typography, Tabs, Tab } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { PAGE_CONTENT_HEIGHT } from '../constants/layout'
import {
  Storage as FhirIcon,
  MenuBook as IgIcon,
  CloudSync as EhrIcon,
} from '@mui/icons-material'
import FhirBrowser from '../components/fhir/FhirBrowser'
import ImplementationGuideBrowser from '../components/fhir/ImplementationGuideBrowser'
import EhrConnectionList from '../components/ehr/EhrConnectionList'

export default function FhirPage() {
  const { t } = useTranslation('fhir')
  const [tabIndex, setTabIndex] = useState(0)

  return (
    <Box sx={{ height: PAGE_CONTENT_HEIGHT, p: 2, display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ mb: 0.5 }}>
          {t('page.title')}
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
          {t('page.subtitle')}
        </Typography>
      </Box>

      <Tabs
        value={tabIndex}
        onChange={(_, v) => setTabIndex(v)}
        sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}
      >
        <Tab icon={<FhirIcon />} iconPosition="start" label={t('page.tabFhirBrowser')} />
        <Tab icon={<IgIcon />} iconPosition="start" label={t('page.tabTwCoreIg')} />
        <Tab icon={<EhrIcon />} iconPosition="start" label={t('page.tabEhrConnections')} />
      </Tabs>

      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {tabIndex === 0 && <FhirBrowser />}
        {tabIndex === 1 && <ImplementationGuideBrowser />}
        {tabIndex === 2 && <EhrConnectionList />}
      </Box>
    </Box>
  )
}
