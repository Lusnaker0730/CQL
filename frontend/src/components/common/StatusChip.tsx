import { memo } from 'react'
import { Chip, type ChipProps } from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection.
import ActiveIcon from '@mui/icons-material/CheckCircle'
import DraftIcon from '@mui/icons-material/Edit'
import RetiredIcon from '@mui/icons-material/Archive'
import ReviewIcon from '@mui/icons-material/RateReview'
import { useTranslation } from 'react-i18next'

const STATUS_CONFIG: Record<string, { color: ChipProps['color']; icon: React.ReactElement; labelKey: string }> = {
  draft: { color: 'default', icon: <DraftIcon sx={{ fontSize: 14 }} />, labelKey: 'status.draft' },
  'in-review': { color: 'info', icon: <ReviewIcon sx={{ fontSize: 14 }} />, labelKey: 'status.inReview' },
  active: { color: 'success', icon: <ActiveIcon sx={{ fontSize: 14 }} />, labelKey: 'status.active' },
  retired: { color: 'warning', icon: <RetiredIcon sx={{ fontSize: 14 }} />, labelKey: 'status.retired' },
}

interface StatusChipProps {
  status: string
  size?: ChipProps['size']
}

function StatusChip({ status, size = 'small' }: StatusChipProps) {
  const { t } = useTranslation()
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.draft
  return (
    <Chip
      label={t(config.labelKey)}
      size={size}
      color={config.color}
      icon={config.icon}
      variant="outlined"
      sx={{ fontWeight: 500 }}
    />
  )
}

export default memo(StatusChip)
