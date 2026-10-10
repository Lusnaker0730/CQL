import { useState } from 'react'
import { Paper, Typography, Tabs, Tab } from '@mui/material'
import { useTranslation } from 'react-i18next'
import ValueSetTab from './ValueSetTab'
import CodeLookupTab from './CodeLookupTab'
import CodeValidationTab from './CodeValidationTab'
import PlatformValueSetTab from './PlatformValueSetTab'
import TabPanel, { a11yProps } from '../common/TabPanel'

export default function TerminologyBrowser() {
  const [tabValue, setTabValue] = useState(0)
  const { t } = useTranslation('terminology')

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        {t('browser.title')}
      </Typography>

      <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)}>
        <Tab label={t('browser.tabValueSetSearch')} {...a11yProps(0, 'terminology')} />
        <Tab label={t('browser.tabCodeLookup')} {...a11yProps(1, 'terminology')} />
        <Tab label={t('browser.tabCodeValidation')} {...a11yProps(2, 'terminology')} />
        <Tab label={t('browser.tabPlatformValueSets')} {...a11yProps(3, 'terminology')} />
      </Tabs>

      <TabPanel value={tabValue} index={0} prefix="terminology" sx={{ py: 2 }}>
        <ValueSetTab />
      </TabPanel>
      <TabPanel value={tabValue} index={1} prefix="terminology" sx={{ py: 2 }}>
        <CodeLookupTab />
      </TabPanel>
      <TabPanel value={tabValue} index={2} prefix="terminology" sx={{ py: 2 }}>
        <CodeValidationTab />
      </TabPanel>
      <TabPanel value={tabValue} index={3} prefix="terminology" sx={{ py: 2 }}>
        <PlatformValueSetTab />
      </TabPanel>
    </Paper>
  )
}
