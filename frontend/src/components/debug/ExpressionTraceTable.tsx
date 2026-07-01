import { useState, useMemo } from 'react'
import {
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  LinearProgress,
  Collapse,
  IconButton,
  Stack,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import {
  KeyboardArrowDown as ExpandIcon,
  KeyboardArrowUp as CollapseIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { ExpressionTrace } from '../../types'

interface ExpressionTraceTableProps {
  traces: ExpressionTrace[]
}

function ExpressionRow({ et, maxExprTime }: { et: ExpressionTrace; maxExprTime: number }) {
  const { t } = useTranslation('common')
  const [open, setOpen] = useState(false)
  const hasDetails = et.sourceLocator || (et.dependencies && et.dependencies.length > 0)

  return (
    <>
      <TableRow hover>
        <TableCell sx={{ width: 40, px: 0.5 }}>
          {hasDetails ? (
            <IconButton size="small" onClick={() => setOpen(!open)}>
              {open ? <CollapseIcon fontSize="small" /> : <ExpandIcon fontSize="small" />}
            </IconButton>
          ) : (
            <Typography
              variant="caption"
              sx={{
                color: "text.secondary",
                pl: 1.5
              }}>
              {et.order + 1}
            </Typography>
          )}
        </TableCell>
        <TableCell>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {et.name}
          </Typography>
        </TableCell>
        <TableCell>
          <Typography
            variant="body2"
            sx={{
              maxWidth: 200,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {et.resultDisplay}
          </Typography>
        </TableCell>
        <TableCell>
          <Chip
            label={et.resultType}
            size="small"
            variant="outlined"
            sx={(theme) => ({ borderColor: alpha(theme.palette.secondary.main, 0.3), color: 'secondary.main' })}
          />
        </TableCell>
        <TableCell>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <LinearProgress
              variant="determinate"
              value={(et.evaluationTimeMs / maxExprTime) * 100}
              sx={(theme) => ({
                flexGrow: 1,
                height: 6,
                borderRadius: 3,
                bgcolor: alpha(theme.palette.primary.main, 0.08),
                '& .MuiLinearProgress-bar': {
                  bgcolor: et.evaluationTimeMs > maxExprTime * 0.7
                    ? theme.palette.warning.dark
                    : theme.palette.primary.main,
                  borderRadius: 3,
                },
              })}
            />
            <Typography variant="caption" sx={{ minWidth: 40, textAlign: 'right' }}>
              {et.evaluationTimeMs}ms
            </Typography>
          </Box>
        </TableCell>
      </TableRow>
      {hasDetails && (
        <TableRow>
          <TableCell colSpan={5} sx={{ py: 0, borderBottom: open ? undefined : 'none' }}>
            <Collapse in={open} timeout="auto" unmountOnExit>
              <Box sx={{ py: 1, pl: 4 }}>
                <Stack spacing={0.5}>
                  {et.sourceLocator && (
                    <Typography variant="caption" sx={{
                      color: "text.secondary"
                    }}>
                      {t('debug.sourceLine', { line: et.sourceLocator })}
                    </Typography>
                  )}
                  {et.dependencies && et.dependencies.length > 0 && (
                    <Stack
                      direction="row"
                      spacing={0.5}
                      useFlexGap
                      sx={{
                        alignItems: "center",
                        flexWrap: "wrap"
                      }}>
                      <Typography variant="caption" sx={{
                        color: "text.secondary"
                      }}>
                        {t('debug.dependsOn')}
                      </Typography>
                      {et.dependencies.map((dep) => (
                        <Chip
                          key={dep}
                          label={dep}
                          size="small"
                          variant="outlined"
                          sx={{ height: 20, fontSize: '0.7rem' }}
                        />
                      ))}
                    </Stack>
                  )}
                </Stack>
              </Box>
            </Collapse>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}

/**
 * Shared expression-trace table used by both the CQL editor execution panel
 * and the CDS debug panel. Sort order locked to `order` field so the rendered
 * sequence matches the library definition order, not insertion order.
 */
export default function ExpressionTraceTable({ traces }: ExpressionTraceTableProps) {
  const { t } = useTranslation('common')

  const sorted = useMemo(
    () => [...traces].sort((a, b) => a.order - b.order),
    [traces]
  )

  const maxExprTime = useMemo(
    () => Math.max(...traces.map((et) => et.evaluationTimeMs), 1),
    [traces]
  )

  if (traces.length === 0) return null

  return (
    <Box>
      <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600, color: 'secondary.main' }}>
        {t('debug.expressionTrace')}
      </Typography>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell scope="col" sx={{ width: 40 }}>{t('debug.index')}</TableCell>
              <TableCell scope="col">{t('debug.expression')}</TableCell>
              <TableCell scope="col">{t('debug.result')}</TableCell>
              <TableCell scope="col">{t('debug.type')}</TableCell>
              <TableCell scope="col" width={160}>{t('debug.time')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sorted.map((et) => (
              <ExpressionRow key={et.name} et={et} maxExprTime={maxExprTime} />
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  )
}
