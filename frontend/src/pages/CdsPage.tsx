import { Box, Grid, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { PAGE_CONTENT_HEIGHT } from '../constants/layout'
import CdsPanel from '../components/cds/CdsPanel'
import CqlEditor from '../components/editor/CqlEditor'

export default function CdsPage() {
  const { t } = useTranslation('cds')

  return (
    <Box sx={{ height: PAGE_CONTENT_HEIGHT, p: 2 }}>
      <Box sx={{ mb: 2 }}>
        <Typography variant="h5" sx={{ mb: 0.5 }}>
          {t('page.title')}
        </Typography>
        <Box
          sx={(theme) => ({
            width: 48,
            height: 3,
            borderRadius: 2,
            background: `linear-gradient(90deg, ${theme.palette.primary.main}, ${theme.palette.primary.light})`,
            mb: 1,
          })}
        />
        <Typography variant="body2" color="text.secondary">
          {t('page.subtitle')}
        </Typography>
      </Box>

      <Grid container spacing={2} sx={{ height: 'calc(100% - 90px)' }}>
        <Grid size={{ xs: 12, md: 6 }}>
          <CqlEditor height="100%" />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <CdsPanel />
        </Grid>
      </Grid>
    </Box>
  )
}
