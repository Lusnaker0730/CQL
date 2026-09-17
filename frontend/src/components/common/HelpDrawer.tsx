import {
  Drawer,
  Typography,
  Box,
  Stack,
  Divider,
  IconButton,
} from '@mui/material'
// Sub-path imports per PAT-161/PR #501: avoid loading the @mui/icons-material
// barrel during vitest collection.
import CloseIcon from '@mui/icons-material/Close'
import { useTranslation } from 'react-i18next'

interface HelpDrawerProps {
  open: boolean
  onClose: () => void
}

const quickStartKeys = [
  'gettingStarted',
  'cqlBasics',
  'cdsHooks',
  'qualityMeasures',
  'fhirBrowser',
  'keyboardShortcuts',
] as const

export default function HelpDrawer({ open, onClose }: HelpDrawerProps) {
  const { t } = useTranslation()

  return (
    <Drawer anchor="right" open={open} onClose={onClose}>
      <Box sx={{ width: 360, p: 3 }}>
        <Stack
          direction="row"
          sx={{
            justifyContent: "space-between",
            alignItems: "center",
            mb: 2
          }}>
          <Typography variant="h6" sx={{
            color: "secondary.main"
          }}>
            {t('help.quickStartGuide')}
          </Typography>
          <IconButton onClick={onClose} aria-label={t('dialogs.closeHelp')}>
            <CloseIcon />
          </IconButton>
        </Stack>

        <Divider sx={{ mb: 2 }} />

        <Stack spacing={3}>
          {quickStartKeys.map((key) => (
            <Box key={key}>
              <Typography variant="subtitle1" sx={{ fontWeight: 600, color: 'primary.main', mb: 0.5 }}>
                {t(`help.quickStart.${key}.title`)}
              </Typography>
              <Typography variant="body2" sx={{
                color: "text.secondary"
              }}>
                {t(`help.quickStart.${key}.content`)}
              </Typography>
            </Box>
          ))}
        </Stack>
      </Box>
    </Drawer>
  );
}
