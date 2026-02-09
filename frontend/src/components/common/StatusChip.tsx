import { Chip, type ChipProps } from '@mui/material'
import {
  CheckCircle as ActiveIcon,
  Edit as DraftIcon,
  Archive as RetiredIcon,
} from '@mui/icons-material'

const STATUS_CONFIG: Record<string, { color: ChipProps['color']; icon: React.ReactElement }> = {
  draft: { color: 'default', icon: <DraftIcon sx={{ fontSize: 14 }} /> },
  active: { color: 'success', icon: <ActiveIcon sx={{ fontSize: 14 }} /> },
  retired: { color: 'warning', icon: <RetiredIcon sx={{ fontSize: 14 }} /> },
}

interface StatusChipProps {
  status: string
  size?: ChipProps['size']
}

export default function StatusChip({ status, size = 'small' }: StatusChipProps) {
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.draft
  return (
    <Chip
      label={status.charAt(0).toUpperCase() + status.slice(1)}
      size={size}
      color={config.color}
      icon={config.icon}
      variant="outlined"
      sx={{ fontWeight: 500 }}
    />
  )
}
