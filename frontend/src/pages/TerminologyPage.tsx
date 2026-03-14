import { Box, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { PAGE_CONTENT_HEIGHT } from '../constants/layout'
import TerminologyBrowser from '../components/terminology/TerminologyBrowser'

export default function TerminologyPage() {
  const { t } = useTranslation('terminology')

  return (
    <Box sx={{ height: PAGE_CONTENT_HEIGHT, p: 2 }}>
      <Box sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ mb: 0.5 }}>
          {t('page.title')}
        </Typography>
        <Box
          sx={{
            width: 48,
            height: 3,
            borderRadius: 2,
            background: 'linear-gradient(90deg, #0D7377, #14A3A8)',
            mb: 1,
          }}
        />
        <Typography variant="body2" color="text.secondary">
          {t('page.subtitle')}
        </Typography>
      </Box>

      <Box sx={{ height: 'calc(100% - 90px)' }}>
        <TerminologyBrowser />
      </Box>
    </Box>
  )
}
