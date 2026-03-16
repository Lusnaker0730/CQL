import { Box, Typography, Link, Stack } from '@mui/material'
import { alpha } from '@mui/material/styles'
import { useSelector } from 'react-redux'
import { useTranslation } from 'react-i18next'
import type { RootState } from '../../store'

const footerLinkSx = {
  color: 'text.secondary',
  textDecoration: 'none',
  transition: 'color 0.2s ease',
  '&:hover': { color: 'primary.main' },
} as const

export default function Footer() {
  const { cursorPosition, errors, warnings } = useSelector((state: RootState) => state.editor)
  const { t } = useTranslation()

  return (
    <Box
      component="footer"
      sx={(theme) => ({
        py: 0.75,
        px: 2,
        bgcolor: 'background.paper',
        borderTop: '1px solid',
        borderColor: alpha(theme.palette.primary.main, 0.1),
      })}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Stack direction="row" spacing={3}>
          <Typography variant="caption" color="text.secondary">
            {t('footer.line', { line: cursorPosition.line, column: cursorPosition.column })}
          </Typography>
          <Typography variant="caption" sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
            <Box
              component="span"
              sx={(theme) => ({
                width: 8,
                height: 8,
                borderRadius: '50%',
                display: 'inline-block',
                bgcolor: errors.length > 0 ? 'error.main' : 'success.main',
                boxShadow: errors.length > 0
                  ? `0 0 0 2px ${alpha(theme.palette.error.main, 0.2)}`
                  : `0 0 0 2px ${alpha(theme.palette.success.main, 0.2)}`,
                ...(errors.length === 0 && {
                  animation: 'pulse 2s ease-in-out infinite',
                  '@keyframes pulse': {
                    '0%, 100%': { opacity: 1 },
                    '50%': { opacity: 0.6 },
                  },
                }),
              })}
            />
            <Box component="span" sx={{ color: errors.length > 0 ? 'error.main' : 'success.main' }}>
              {errors.length > 0 ? t('footer.errorsCount', { count: errors.length }) : t('footer.noErrors')}
            </Box>
          </Typography>
          <Typography variant="caption" sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
            <Box
              component="span"
              sx={(theme) => ({
                width: 8,
                height: 8,
                borderRadius: '50%',
                display: 'inline-block',
                bgcolor: warnings.length > 0 ? 'warning.main' : 'text.disabled',
                boxShadow: warnings.length > 0
                  ? `0 0 0 2px ${alpha(theme.palette.warning.main, 0.2)}`
                  : 'none',
              })}
            />
            <Box component="span" sx={{ color: warnings.length > 0 ? 'warning.main' : 'text.secondary' }}>
              {warnings.length > 0 ? t('footer.warningsCount', { count: warnings.length }) : t('footer.noWarnings')}
            </Box>
          </Typography>
        </Stack>

        <Stack direction="row" spacing={2}>
          <Link
            href="https://cql.hl7.org/"
            target="_blank"
            rel="noopener"
            variant="caption"
            sx={footerLinkSx}
          >
            {t('footer.cqlSpec')}
          </Link>
          <Link
            href="https://cds-hooks.org/"
            target="_blank"
            rel="noopener"
            variant="caption"
            sx={footerLinkSx}
          >
            {t('footer.cdsHooks')}
          </Link>
          <Link
            href="https://www.hl7.org/fhir/"
            target="_blank"
            rel="noopener"
            variant="caption"
            sx={footerLinkSx}
          >
            {t('footer.fhir')}
          </Link>
        </Stack>
      </Stack>
    </Box>
  )
}
