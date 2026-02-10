import {
  Drawer,
  Typography,
  Box,
  Stack,
  Divider,
  IconButton,
} from '@mui/material'
import { Close as CloseIcon } from '@mui/icons-material'
import { helpContent } from '../../constants/helpContent'

interface HelpDrawerProps {
  open: boolean
  onClose: () => void
}

export default function HelpDrawer({ open, onClose }: HelpDrawerProps) {
  return (
    <Drawer anchor="right" open={open} onClose={onClose}>
      <Box sx={{ width: 360, p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6" color="secondary.main">
            Quick Start Guide
          </Typography>
          <IconButton onClick={onClose} aria-label="Close help drawer">
            <CloseIcon />
          </IconButton>
        </Stack>

        <Divider sx={{ mb: 2 }} />

        <Stack spacing={3}>
          {helpContent.quickStart.map((section, index) => (
            <Box key={index}>
              <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'primary.main', mb: 0.5 }}>
                {section.title}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {section.content}
              </Typography>
            </Box>
          ))}
        </Stack>
      </Box>
    </Drawer>
  )
}
