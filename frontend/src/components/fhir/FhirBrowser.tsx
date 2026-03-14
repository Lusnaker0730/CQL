import { useState } from 'react'
import {
  Paper,
  Typography,
  Stack,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Tabs,
  Tab,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import FhirServerUrlField from '../common/FhirServerUrlField'
import TabPanel, { a11yProps } from '../common/TabPanel'
import { FHIR_RESOURCE_TYPES } from '../../utils/fhirBrowserUtils'
import SearchTab from './SearchTab'
import ReadTab from './ReadTab'
import ValidateTab from './ValidateTab'
import TerminologyTab from './TerminologyTab'
import TransactionTab from './TransactionTab'
import BulkExportTab from './BulkExportTab'
import { DEFAULT_FHIR_SERVER_URL } from '../../config/env'

export default function FhirBrowser() {
  const { t } = useTranslation('fhir')
  const [tabValue, setTabValue] = useState(0)
  const [fhirServer, setFhirServer] = useState(DEFAULT_FHIR_SERVER_URL)
  const [fhirServerError, setFhirServerError] = useState<string | null>(null)
  const [resourceType, setResourceType] = useState('Patient')

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        {t('browser.title')}
      </Typography>

      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="flex-start">
          <FhirServerUrlField
            value={fhirServer}
            onChange={(value) => {
              setFhirServer(value)
              setFhirServerError(null)
            }}
            error={!!fhirServerError}
            helperText={fhirServerError}
          />
          <HelpTooltip text={helpContent.fhir.serverUrl} />
        </Stack>

        <FormControl fullWidth size="small">
          <InputLabel>{t('browser.resourceTypeLabel')}</InputLabel>
          <Select
            value={resourceType}
            onChange={(e) => setResourceType(e.target.value)}
            label={t('browser.resourceTypeLabel')}
          >
            {FHIR_RESOURCE_TYPES.map((type) => (
              <MenuItem key={type} value={type}>
                {type}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Tabs
          value={tabValue}
          onChange={(_, v) => setTabValue(v)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label={t('browser.tabSearch')} {...a11yProps(0, 'fhir')} />
          <Tab label={t('browser.tabRead')} {...a11yProps(1, 'fhir')} />
          <Tab label={t('browser.tabValidate')} {...a11yProps(2, 'fhir')} />
          <Tab label={t('browser.tabTerminology')} {...a11yProps(3, 'fhir')} />
          <Tab label={t('browser.tabTransaction')} {...a11yProps(4, 'fhir')} />
          <Tab label={t('browser.tabBulkExport')} {...a11yProps(5, 'fhir')} />
        </Tabs>

        <TabPanel value={tabValue} index={0} prefix="fhir" sx={{ py: 2 }}>
          <SearchTab fhirServer={fhirServer} resourceType={resourceType} />
        </TabPanel>

        <TabPanel value={tabValue} index={1} prefix="fhir" sx={{ py: 2 }}>
          <ReadTab fhirServer={fhirServer} resourceType={resourceType} />
        </TabPanel>

        <TabPanel value={tabValue} index={2} prefix="fhir" sx={{ py: 2 }}>
          <ValidateTab />
        </TabPanel>

        <TabPanel value={tabValue} index={3} prefix="fhir" sx={{ py: 2 }}>
          <TerminologyTab />
        </TabPanel>

        <TabPanel value={tabValue} index={4} prefix="fhir" sx={{ py: 2 }}>
          <TransactionTab fhirServer={fhirServer} />
        </TabPanel>

        <TabPanel value={tabValue} index={5} prefix="fhir" sx={{ py: 2 }}>
          <BulkExportTab fhirServer={fhirServer} />
        </TabPanel>
      </Stack>
    </Paper>
  )
}
