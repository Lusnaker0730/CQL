import { Stack, TextField, MenuItem } from '@mui/material'
import { useTranslation } from 'react-i18next'
import DepartmentSelector from '../common/DepartmentSelector'

interface DashboardFilterBarProps {
  department: string
  onDepartmentChange: (value: string) => void
  periodType: string
  onPeriodTypeChange: (value: string) => void
}

export default function DashboardFilterBar({
  department,
  onDepartmentChange,
  periodType,
  onPeriodTypeChange,
}: DashboardFilterBarProps) {
  const { t } = useTranslation('measures')

  return (
    <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
      <DepartmentSelector
        value={department}
        onChange={onDepartmentChange}
        size="small"
        fullWidth={false}
      />
      <TextField
        select
        size="small"
        label={t('dashboard.periodType')}
        value={periodType}
        onChange={(e) => onPeriodTypeChange(e.target.value)}
        sx={{ minWidth: 150 }}
      >
        <MenuItem value="monthly">{t('dashboard.monthly')}</MenuItem>
        <MenuItem value="quarterly">{t('dashboard.quarterly')}</MenuItem>
        <MenuItem value="yearly">{t('dashboard.yearly')}</MenuItem>
      </TextField>
    </Stack>
  )
}
