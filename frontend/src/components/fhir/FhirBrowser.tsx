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
import { useQuery } from '@tanstack/react-query'
import HelpTooltip from '../common/HelpTooltip'
import { helpContent } from '../../constants/helpContent'
import TabPanel, { a11yProps } from '../common/TabPanel'
import { FHIR_RESOURCE_TYPES } from '../../utils/fhirBrowserUtils'
import SearchTab from './SearchTab'
import ReadTab from './ReadTab'
import ValidateTab from './ValidateTab'
import TerminologyTab from './TerminologyTab'
import TransactionTab from './TransactionTab'
import BulkExportTab from './BulkExportTab'
import { ehrApi } from '../../api'

// PAT-212: the FHIR browser targets a tenant-scoped EHR connection instead of an
// arbitrary server URL. The sentinel "" selects the shared sandbox (connectionId = null);
// every other value is a connection id owned by the caller's tenant.
const SANDBOX = ''

export default function FhirBrowser() {
  const { t } = useTranslation('fhir')
  const [tabValue, setTabValue] = useState(0)
  const [connectionValue, setConnectionValue] = useState<string>(SANDBOX)
  const [resourceType, setResourceType] = useState('Patient')

  const { data: connections } = useQuery({
    queryKey: ['ehr', 'connections'],
    queryFn: () => ehrApi.getConnections(),
  })

  const connectionId = connectionValue === SANDBOX ? null : Number(connectionValue)

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        {t('browser.title')}
      </Typography>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} sx={{
          alignItems: "center"
        }}>
          <FormControl fullWidth size="small">
            <InputLabel>{t('browser.connectionLabel')}</InputLabel>
            <Select
              value={connectionValue}
              onChange={(e) => setConnectionValue(e.target.value)}
              label={t('browser.connectionLabel')}
            >
              <MenuItem value={SANDBOX}>{t('browser.sandboxOption')}</MenuItem>
              {(connections ?? []).map((c) => (
                <MenuItem key={c.id} value={String(c.id)}>
                  {c.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
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
          <SearchTab connectionId={connectionId} resourceType={resourceType} />
        </TabPanel>

        <TabPanel value={tabValue} index={1} prefix="fhir" sx={{ py: 2 }}>
          <ReadTab connectionId={connectionId} resourceType={resourceType} />
        </TabPanel>

        <TabPanel value={tabValue} index={2} prefix="fhir" sx={{ py: 2 }}>
          <ValidateTab />
        </TabPanel>

        <TabPanel value={tabValue} index={3} prefix="fhir" sx={{ py: 2 }}>
          <TerminologyTab />
        </TabPanel>

        <TabPanel value={tabValue} index={4} prefix="fhir" sx={{ py: 2 }}>
          <TransactionTab connectionId={connectionId} />
        </TabPanel>

        <TabPanel value={tabValue} index={5} prefix="fhir" sx={{ py: 2 }}>
          <BulkExportTab connectionId={connectionId} />
        </TabPanel>
      </Stack>
    </Paper>
  );
}
