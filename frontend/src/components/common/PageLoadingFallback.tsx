import { Box, CircularProgress, Typography, Stack } from '@mui/material'

export default function PageLoadingFallback() {
  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: 'calc(100vh - 120px)',
        background: (theme) =>
          theme.palette.mode === 'dark'
            ? 'linear-gradient(180deg, #1a1a1a 0%, #121212 100%)'
            : 'linear-gradient(180deg, #EDF2F6 0%, #F4F7F9 50%, #F9FBFC 100%)',
      }}
    >
      <Stack alignItems="center" spacing={2}>
        <CircularProgress sx={{ color: '#0D7377' }} />
        <Typography variant="body2" color="text.secondary">
          Loading...
        </Typography>
      </Stack>
    </Box>
  )
}
