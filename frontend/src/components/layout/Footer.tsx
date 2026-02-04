import { Box, Typography, Link, Stack } from '@mui/material'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'

export default function Footer() {
  const { cursorPosition, errors, warnings } = useSelector((state: RootState) => state.editor)

  return (
    <Box
      component="footer"
      sx={{
        py: 1,
        px: 2,
        bgcolor: 'grey.100',
        borderTop: '1px solid',
        borderColor: 'grey.300',
      }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Stack direction="row" spacing={3}>
          <Typography variant="caption" color="text.secondary">
            Ln {cursorPosition.line}, Col {cursorPosition.column}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {errors.length > 0 ? (
              <span style={{ color: 'red' }}>{errors.length} errors</span>
            ) : (
              <span style={{ color: 'green' }}>No errors</span>
            )}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {warnings.length > 0 ? (
              <span style={{ color: 'orange' }}>{warnings.length} warnings</span>
            ) : (
              <span>No warnings</span>
            )}
          </Typography>
        </Stack>

        <Stack direction="row" spacing={2}>
          <Link
            href="https://cql.hl7.org/"
            target="_blank"
            rel="noopener"
            variant="caption"
            color="text.secondary"
          >
            CQL Specification
          </Link>
          <Link
            href="https://cds-hooks.org/"
            target="_blank"
            rel="noopener"
            variant="caption"
            color="text.secondary"
          >
            CDS Hooks
          </Link>
          <Link
            href="https://www.hl7.org/fhir/"
            target="_blank"
            rel="noopener"
            variant="caption"
            color="text.secondary"
          >
            FHIR
          </Link>
        </Stack>
      </Stack>
    </Box>
  )
}
