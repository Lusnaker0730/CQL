import { useState } from 'react'
import { Box, Paper, Typography, Tabs, Tab } from '@mui/material'
import ValueSetTab from './ValueSetTab'
import CodeLookupTab from './CodeLookupTab'
import CodeValidationTab from './CodeValidationTab'

interface TabPanelProps {
  children?: React.ReactNode
  index: number
  value: number
}

function TabPanel({ children, value, index }: TabPanelProps) {
  return (
    <div role="tabpanel" hidden={value !== index}>
      {value === index && <Box sx={{ py: 2 }}>{children}</Box>}
    </div>
  )
}

export default function TerminologyBrowser() {
  const [tabValue, setTabValue] = useState(0)

  return (
    <Paper sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Typography variant="h6" gutterBottom>
        Terminology Browser
      </Typography>

      <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)}>
        <Tab label="ValueSet Search" />
        <Tab label="Code Lookup" />
        <Tab label="Code Validation" />
      </Tabs>

      <TabPanel value={tabValue} index={0}>
        <ValueSetTab />
      </TabPanel>
      <TabPanel value={tabValue} index={1}>
        <CodeLookupTab />
      </TabPanel>
      <TabPanel value={tabValue} index={2}>
        <CodeValidationTab />
      </TabPanel>
    </Paper>
  )
}
