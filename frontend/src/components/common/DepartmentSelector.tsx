import { TextField, MenuItem } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { departmentApi } from '../../api/departmentApi'

interface DepartmentSelectorProps {
  value: string
  onChange: (value: string) => void
  label?: string
  size?: 'small' | 'medium'
  fullWidth?: boolean
  disabled?: boolean
  showAll?: boolean
}

export default function DepartmentSelector({
  value,
  onChange,
  label,
  size = 'small',
  fullWidth = true,
  disabled,
  showAll = true,
}: DepartmentSelectorProps) {
  const { t } = useTranslation('common')
  const { data: departments = [] } = useQuery({
    queryKey: ['departments'],
    queryFn: departmentApi.getAll,
    staleTime: 5 * 60 * 1000,
  })

  return (
    <TextField
      select
      label={label || t('department.label')}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      size={size}
      fullWidth={fullWidth}
      disabled={disabled}
    >
      {showAll && (
        <MenuItem value="">
          <em>{t('department.all')}</em>
        </MenuItem>
      )}
      {departments.map((dept) => (
        <MenuItem key={dept.code} value={dept.code}>
          {dept.name}
        </MenuItem>
      ))}
    </TextField>
  )
}
