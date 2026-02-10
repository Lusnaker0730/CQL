import { useState } from 'react'
import { Paper, Typography, Tabs, Tab } from '@mui/material'
import ValueSetTab from './ValueSetTab'
import CodeLookupTab from './CodeLookupTab'
import CodeValidationTab from './CodeValidationTab'
import TabPanel, { a11yProps } from '../common/TabPanel'

export default function TerminologyBrowser() {
  const [tabValue, setTabValue] = useState(0)

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        Terminology Browser
      </Typography>

      <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)}>
        <Tab label="ValueSet Search" {...a11yProps(0, 'terminology')} />
        <Tab label="Code Lookup" {...a11yProps(1, 'terminology')} />
        <Tab label="Code Validation" {...a11yProps(2, 'terminology')} />
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
    </Paper>
  )
}
