import { memo } from 'react'
import { IconButton, Tooltip } from '@mui/material'
import { HelpOutline as HelpIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'

interface HelpTooltipProps {
  text: string
}

function HelpTooltip({ text }: HelpTooltipProps) {
  const { t } = useTranslation()
  // If text looks like an i18n key (contains dots), resolve it; otherwise use as-is
  const resolved = text.includes('.') ? t(text) : text
  return (
    <Tooltip title={resolved} arrow placement="top">
      <IconButton size="small" sx={{ color: 'text.secondary', p: 0.25 }} aria-label={t('toolbar.help')}>
        <HelpIcon sx={{ fontSize: 16 }} />
      </IconButton>
    </Tooltip>
  )
}

export default memo(HelpTooltip)
