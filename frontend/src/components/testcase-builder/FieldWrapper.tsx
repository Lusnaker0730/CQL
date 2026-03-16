import type { ReactNode } from 'react'
import { Box, Typography } from '@mui/material'

interface FieldWrapperProps {
  name: string
  isRequired?: boolean
  extra?: ReactNode
  children: ReactNode
}

export default function FieldWrapper({ name, isRequired, extra, children }: FieldWrapperProps) {
  return (
    <Box sx={{ mb: 1, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
        {name} {isRequired && '*'}
        {extra}
      </Typography>
      {children}
    </Box>
  )
}
