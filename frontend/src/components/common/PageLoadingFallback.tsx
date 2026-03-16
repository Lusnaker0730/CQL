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
            ? `linear-gradient(180deg, ${theme.palette.grey[900]} 0%, ${theme.palette.background.default} 100%)`
            : `linear-gradient(180deg, ${theme.palette.background.default} 0%, ${theme.palette.background.paper} 50%, ${theme.palette.background.paper} 100%)`,
      }}
    >
      <Stack alignItems="center" spacing={2}>
        <CircularProgress sx={{ color: 'primary.main' }} />
        <Typography variant="body2" color="text.secondary">
          {t('status.loading')}
        </Typography>
      </Stack>
    </Box>
  )
}

export default memo(PageLoadingFallback)
