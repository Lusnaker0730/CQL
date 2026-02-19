import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Tab, Tabs } from '@mui/material'
import ExpressionBuilder from './ExpressionBuilder'
import OperatorReference from './OperatorReference'

interface OperatorPanelProps {
  expressions: string[]
  parameters: string[]
  valueSets: string[]
  onInsert: (snippet: string) => void
  onCancel: () => void
}

export default function OperatorPanel({
  expressions,
  parameters,
  valueSets,
  onInsert,
  onCancel,
}: OperatorPanelProps) {
  const { t } = useTranslation('builder')
  const [tab, setTab] = useState(0)

  return (
    <Box>
      <Tabs
        value={tab}
        onChange={(_, v) => setTab(v)}
        variant="fullWidth"
        sx={{
          minHeight: 32,
          mb: 1,
          '& .MuiTab-root': { minHeight: 32, py: 0.5, fontSize: '0.75rem', textTransform: 'none' },
        }}
      >
        <Tab label={t('operatorPanel.expressionBuilder')} />
        <Tab label={t('operatorPanel.operatorReference')} />
      </Tabs>

      {tab === 0 && (
        <ExpressionBuilder
          expressions={expressions}
          parameters={parameters}
          valueSets={valueSets}
          onInsert={onInsert}
          onCancel={onCancel}
        />
      )}

      {tab === 1 && (
        <OperatorReference onInsert={onInsert} />
      )}
    </Box>
  )
}
