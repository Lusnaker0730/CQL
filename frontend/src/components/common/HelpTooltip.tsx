import { IconButton, Tooltip } from '@mui/material'
import { HelpOutline as HelpIcon } from '@mui/icons-material'

interface HelpTooltipProps {
  text: string
}

export default function HelpTooltip({ text }: HelpTooltipProps) {
  return (
    <Tooltip title={text} arrow placement="top">
      <IconButton size="small" sx={{ color: 'text.secondary', p: 0.25 }}>
        <HelpIcon sx={{ fontSize: 16 }} />
      </IconButton>
    </Tooltip>
  )
}
