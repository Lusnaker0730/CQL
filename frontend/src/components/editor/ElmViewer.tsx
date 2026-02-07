import React from 'react'
import { useMemo } from 'react'
import { Box, Typography, Paper, Tabs, Tab, Chip, Stack } from '@mui/material'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'

interface TabPanelProps {
  children?: React.ReactNode
  index: number
  value: number
}

function TabPanel({ children, value, index }: TabPanelProps) {
  return (
    <div role="tabpanel" hidden={value !== index}>
      {value === index && <Box sx={{ p: 2 }}>{children}</Box>}
    </div>
  )
}

export default function ElmViewer() {
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
        <Tab label="Metadata" />
        <Tab
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
          label={
            <Stack direction="row" spacing={1} alignItems="center">
              <span>Warnings</span>
              {warnings.length > 0 && (
                <Chip label={warnings.length} color="warning" size="small" />
              )}
            </Stack>
          }
        />
        <Tab label="ELM JSON" />
      </Tabs>

      <TabPanel value={tabValue} index={0}>
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

      <TabPanel value={tabValue} index={1}>
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

      <TabPanel value={tabValue} index={2}>
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

      <TabPanel value={tabValue} index={3}>
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
    </Paper>
  )
}
