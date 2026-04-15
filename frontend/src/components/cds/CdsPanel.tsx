import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'

import { Paper, Typography, Tabs, Tab } from '@mui/material'
import { Analytics as AnalyticsIcon, VpnKey as KeyIcon, History as HistoryIcon } from '@mui/icons-material'
import TabPanel, { a11yProps } from '../common/TabPanel'
import InvokeServicePanel from './InvokeServicePanel'
import ManageServicesPanel from './ManageServicesPanel'
import AnalyticsPanel from './AnalyticsPanel'
import SandboxPanel from './SandboxPanel'
import ApiKeyManager from './ApiKeyManager'
import RecentInvocationsPanel from './RecentInvocationsPanel'

export default function CdsPanel() {
  const [tabValue, setTabValue] = useState(0)
  const { t } = useTranslation('cds')
  const userRole = useSelector((state: RootState) => state.auth.user?.role)
  const isAdmin = userRole === 'ADMIN' || userRole === 'DEPARTMENT_ADMIN'

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        {t('panel.title')}
      </Typography>

      <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)} variant="scrollable" scrollButtons="auto">
        <Tab label={t('panel.tabInvoke')} {...a11yProps(0, 'cds')} />
        <Tab label={t('panel.tabManage')} {...a11yProps(1, 'cds')} />
        <Tab label={t('panel.tabAnalytics')} icon={<AnalyticsIcon />} iconPosition="start" {...a11yProps(2, 'cds')} />
        <Tab label={t('panel.tabSandbox')} {...a11yProps(3, 'cds')} />
        <Tab label={t('panel.tabApiKeys')} icon={<KeyIcon />} iconPosition="start" {...a11yProps(4, 'cds')} />
        {isAdmin && (
          <Tab label={t('panel.tabRecent')} icon={<HistoryIcon />} iconPosition="start" {...a11yProps(5, 'cds')} />
        )}
      </Tabs>

      <TabPanel value={tabValue} index={0} prefix="cds" sx={{ pt: 2 }}>
        <InvokeServicePanel />
      </TabPanel>
      <TabPanel value={tabValue} index={1} prefix="cds" sx={{ pt: 2 }}>
        <ManageServicesPanel />
      </TabPanel>
      <TabPanel value={tabValue} index={2} prefix="cds" sx={{ pt: 2 }}>
        <AnalyticsPanel />
      </TabPanel>
      <TabPanel value={tabValue} index={3} prefix="cds" sx={{ pt: 2 }}>
        <SandboxPanel />
      </TabPanel>
      <TabPanel value={tabValue} index={4} prefix="cds" sx={{ pt: 2 }}>
        <ApiKeyManager />
      </TabPanel>
      {isAdmin && (
        <TabPanel value={tabValue} index={5} prefix="cds" sx={{ pt: 2 }}>
          <RecentInvocationsPanel />
        </TabPanel>
      )}
    </Paper>
  )
}
