import { memo } from 'react'
import { Stack, Typography, type StackProps } from '@mui/material'
import HelpTooltip from './HelpTooltip'

interface SectionHeaderProps extends Omit<StackProps, 'title'> {
  title: string
  helpText?: string
  actions?: React.ReactNode
}

function SectionHeader({ title, helpText, actions, ...stackProps }: SectionHeaderProps) {
  return (
    <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2} {...stackProps}>
      <Stack direction="row" spacing={0.5} alignItems="center">
        <Typography variant="h6">{title}</Typography>
        {helpText && <HelpTooltip text={helpText} />}
      </Stack>
      {actions && (
        <Stack direction="row" spacing={1}>
          {actions}
        </Stack>
      )}
    </Stack>
  )
}

export default memo(SectionHeader)
