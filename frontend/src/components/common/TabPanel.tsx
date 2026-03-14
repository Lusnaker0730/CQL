import { Box } from '@mui/material'
import type { SxProps, Theme } from '@mui/material'

interface TabPanelProps {
  children?: React.ReactNode
  index: number
  value: number
  prefix: string
  sx?: SxProps<Theme>
}

// eslint-disable-next-line react-refresh/only-export-components
export function a11yProps(index: number, prefix: string) {
  return {
    id: `${prefix}-tab-${index}`,
    'aria-controls': `${prefix}-tabpanel-${index}`,
  }
}

export default function TabPanel({ children, value, index, prefix, sx }: TabPanelProps) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`${prefix}-tabpanel-${index}`}
      aria-labelledby={`${prefix}-tab-${index}`}
    >
      {value === index && <Box sx={sx}>{children}</Box>}
    </div>
  )
}
