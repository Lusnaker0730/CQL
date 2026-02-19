import { memo } from 'react'
import { Box, CircularProgress, Typography, Stack } from '@mui/material'
import { PAGE_CONTENT_HEIGHT } from '../../constants/layout'
import { useTranslation } from 'react-i18next'

function PageLoadingFallback() {
  const { t } = useTranslation()
  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: PAGE_CONTENT_HEIGHT,
        background: (theme) =>
          theme.palette.mode === 'dark'
            ? 'linear-gradient(180deg, #1a1a1a 0%, #121212 100%)'
            : 'linear-gradient(180deg, #EDF2F6 0%, #F4F7F9 50%, #F9FBFC 100%)',
      }}
    >
      <Stack alignItems="center" spacing={2}>
        <CircularProgress sx={{ color: '#0D7377' }} />
        <Typography variant="body2" color="text.secondary">
          {t('status.loading')}
        </Typography>
      </Stack>
    </Box>
  )
}

export default memo(PageLoadingFallback)
