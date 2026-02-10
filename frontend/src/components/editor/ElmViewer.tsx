import React from 'react'
import { useMemo } from 'react'
import {
  Box,
  Typography,
  Paper,
  Tabs,
  Tab,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  CircularProgress,
} from '@mui/material'
import {
  CheckCircle as ValidIcon,
  Error as ErrorIcon,
  Info as InfoIcon,
} from '@mui/icons-material'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'
import type { TerminologyValidationItem } from '../../types'
import TabPanel, { a11yProps } from '../common/TabPanel'

interface ElmViewerProps {
  terminologyResults?: TerminologyValidationItem[]
  isTermValidating?: boolean
}

export default function ElmViewer({ terminologyResults = [], isTermValidating = false }: ElmViewerProps) {
  const { elmJson, errors, warnings } = useSelector((state: RootState) => state.editor)
  const [tabValue, setTabValue] = React.useState(0)

  const parsedElm = useMemo(() => {
    if (!elmJson) return null
    try {
      return JSON.parse(elmJson)
    } catch {
      return null
    }
  }, [elmJson])

  const metadata = useMemo(() => {
    if (!parsedElm) return null
    return {
      libraryId: parsedElm.library?.identifier?.id,
      version: parsedElm.library?.identifier?.version,
      usings: parsedElm.library?.usings?.def || [],
      includes: parsedElm.library?.includes?.def || [],
      parameters: parsedElm.library?.parameters?.def || [],
      valueSets: parsedElm.library?.valueSets?.def || [],
      codes: parsedElm.library?.codes?.def || [],
      statements: parsedElm.library?.statements?.def || [],
    }
  }, [parsedElm])

  return (
    <Paper sx={{ height: '100%', overflow: 'auto' }}>
      <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)}>
        <Tab label="Metadata" {...a11yProps(0, 'elm')} />
        <Tab
          {...a11yProps(1, 'elm')}
          label={
            <Stack direction="row" spacing={1} alignItems="center">
              <span>Errors</span>
              {errors.length > 0 && (
                <Chip label={errors.length} color="error" size="small" />
              )}
            </Stack>
          }
        />
        <Tab
          {...a11yProps(2, 'elm')}
          label={
            <Stack direction="row" spacing={1} alignItems="center">
              <span>Warnings</span>
              {warnings.length > 0 && (
                <Chip label={warnings.length} color="warning" size="small" />
              )}
            </Stack>
          }
        />
        <Tab label="ELM JSON" {...a11yProps(3, 'elm')} />
        <Tab
          {...a11yProps(4, 'elm')}
          label={
            <Stack direction="row" spacing={1} alignItems="center">
              <span>Terminology</span>
              {isTermValidating ? (
                <CircularProgress size={14} />
              ) : terminologyResults.length > 0 ? (
                <Chip
                  label={terminologyResults.length}
                  size="small"
                  color={terminologyResults.every((r) => r.status === 'valid') ? 'success' : 'warning'}
                />
              ) : null}
            </Stack>
          }
        />
      </Tabs>

      <TabPanel value={tabValue} index={0} prefix="elm" sx={{ p: 2 }}>
        {metadata ? (
          <Stack spacing={2}>
            <Box>
              <Typography variant="subtitle2" color="text.secondary">
                Library
              </Typography>
              <Typography sx={{ color: 'text.primary', fontWeight: 500 }}>
                {metadata.libraryId} v{metadata.version}
              </Typography>
            </Box>

            {metadata.usings.length > 0 && (
              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  Using
                </Typography>
                {metadata.usings.map((u: { localIdentifier: string; version: string }, i: number) => (
                  <Chip
                    key={i}
                    label={`${u.localIdentifier} v${u.version}`}
                    size="small"
                    sx={{
                      mr: 1,
                      mb: 1,
                      bgcolor: 'rgba(13,115,119,0.08)',
                      color: 'primary.dark',
                      fontWeight: 500,
                    }}
                  />
                ))}
              </Box>
            )}

            {metadata.includes.length > 0 && (
              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  Includes
                </Typography>
                {metadata.includes.map((inc: { localIdentifier: string; path: string; version: string }, i: number) => (
                  <Chip
                    key={i}
                    label={`${inc.localIdentifier} (${inc.path} v${inc.version})`}
                    size="small"
                    sx={{
                      mr: 1,
                      mb: 1,
                      bgcolor: 'rgba(27,58,92,0.08)',
                      color: 'secondary.main',
                      fontWeight: 500,
                    }}
                  />
                ))}
              </Box>
            )}

            {metadata.parameters.length > 0 && (
              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  Parameters
                </Typography>
                {metadata.parameters.map((p: { name: string }, i: number) => (
                  <Chip
                    key={i}
                    label={p.name}
                    size="small"
                    sx={{
                      mr: 1,
                      mb: 1,
                      bgcolor: 'rgba(13,115,119,0.08)',
                      color: 'primary.dark',
                      fontWeight: 500,
                    }}
                  />
                ))}
              </Box>
            )}

            {metadata.valueSets.length > 0 && (
              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  Value Sets
                </Typography>
                {metadata.valueSets.map((vs: { name: string }, i: number) => (
                  <Chip
                    key={i}
                    label={vs.name}
                    size="small"
                    sx={{
                      mr: 1,
                      mb: 1,
                      bgcolor: 'rgba(46,125,50,0.08)',
                      color: 'success.dark',
                      fontWeight: 500,
                    }}
                  />
                ))}
              </Box>
            )}

            {metadata.statements.length > 0 && (
              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  Expressions ({metadata.statements.length})
                </Typography>
                {metadata.statements.map((stmt: { name: string; context: string }, i: number) => (
                  <Chip
                    key={i}
                    label={`${stmt.name}${stmt.context ? ` (${stmt.context})` : ''}`}
                    size="small"
                    variant="outlined"
                    sx={{
                      mr: 1,
                      mb: 1,
                      borderColor: 'rgba(13,115,119,0.3)',
                      color: 'primary.dark',
                    }}
                  />
                ))}
              </Box>
            )}
          </Stack>
        ) : (
          <Typography color="text.secondary">
            No ELM data available. Translate CQL to view metadata.
          </Typography>
        )}
      </TabPanel>

      <TabPanel value={tabValue} index={1} prefix="elm" sx={{ p: 2 }}>
        {errors.length > 0 ? (
          <Stack spacing={1}>
            {errors.map((error, i) => (
              <Paper
                key={i}
                elevation={0}
                sx={{
                  p: 2,
                  bgcolor: 'rgba(211,47,47,0.06)',
                  borderLeft: '4px solid',
                  borderLeftColor: 'error.main',
                  borderRadius: '0 8px 8px 0',
                }}
              >
                <Typography variant="body2" fontWeight="bold" sx={{ color: 'error.dark' }}>
                  Line {error.startLine}:{error.startColumn}
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.primary' }}>{error.message}</Typography>
              </Paper>
            ))}
          </Stack>
        ) : (
          <Typography color="success.main">No errors</Typography>
        )}
      </TabPanel>

      <TabPanel value={tabValue} index={2} prefix="elm" sx={{ p: 2 }}>
        {warnings.length > 0 ? (
          <Stack spacing={1}>
            {warnings.map((warning, i) => (
              <Paper
                key={i}
                elevation={0}
                sx={{
                  p: 2,
                  bgcolor: 'rgba(237,108,2,0.06)',
                  borderLeft: '4px solid',
                  borderLeftColor: 'warning.main',
                  borderRadius: '0 8px 8px 0',
                }}
              >
                <Typography variant="body2" fontWeight="bold" sx={{ color: 'warning.dark' }}>
                  Line {warning.startLine}:{warning.startColumn}
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.primary' }}>{warning.message}</Typography>
              </Paper>
            ))}
          </Stack>
        ) : (
          <Typography color="text.secondary">No warnings</Typography>
        )}
      </TabPanel>

      <TabPanel value={tabValue} index={3} prefix="elm" sx={{ p: 2 }}>
        {elmJson ? (
          <Box
            component="pre"
            sx={{
              p: 2,
              bgcolor: '#F8FAFB',
              borderRadius: '8px',
              border: '1px solid rgba(13,115,119,0.1)',
              overflow: 'auto',
              fontSize: '0.75rem',
              maxHeight: 400,
              fontFamily: '"JetBrains Mono", "Fira Code", "Consolas", monospace',
              color: 'text.primary',
            }}
          >
            {JSON.stringify(parsedElm, null, 2)}
          </Box>
        ) : (
          <Typography color="text.secondary">
            No ELM data available. Translate CQL to view ELM JSON.
          </Typography>
        )}
      </TabPanel>

      <TabPanel value={tabValue} index={4} prefix="elm" sx={{ p: 2 }}>
        {isTermValidating ? (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CircularProgress size={20} />
            <Typography color="text.secondary">Validating terminology references...</Typography>
          </Box>
        ) : terminologyResults.length > 0 ? (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell scope="col" sx={{ fontWeight: 600, width: 40 }}>Status</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>Type</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>Name</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>Reference</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>Detail</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {terminologyResults.map((item, i) => (
                  <TableRow key={i}>
                    <TableCell>
                      {item.status === 'valid' ? (
                        <ValidIcon fontSize="small" color="success" />
                      ) : item.status === 'error' ? (
                        <ErrorIcon fontSize="small" color="error" />
                      ) : (
                        <InfoIcon fontSize="small" color="warning" />
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={item.type}
                        size="small"
                        variant="outlined"
                        color={item.type === 'valueset' ? 'success' : item.type === 'code' ? 'primary' : 'default'}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" fontWeight={500}>{item.name}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ wordBreak: 'break-all' }}>
                        {item.url || (item.system ? `${item.system} | ${item.code}` : item.code || '—')}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">{item.detail || '—'}</Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        ) : (
          <Typography color="text.secondary">
            No terminology references found. Translate CQL to validate terminology.
          </Typography>
        )}
      </TabPanel>
    </Paper>
  )
}
