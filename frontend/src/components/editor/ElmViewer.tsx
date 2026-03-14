import React, { useState } from 'react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
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
  Button,
  Collapse,
} from '@mui/material'
import {
  CheckCircle as ValidIcon,
  Error as ErrorIcon,
  Info as InfoIcon,
  AutoFixHigh as AiFixIcon,
} from '@mui/icons-material'
import { useSelector } from 'react-redux'
import type { RootState } from '../../store'
import type { TerminologyValidationItem } from '../../types'
import { useFixSuggestion } from '../../hooks/useCql'
import TabPanel, { a11yProps } from '../common/TabPanel'

interface ElmViewerProps {
  terminologyResults?: TerminologyValidationItem[]
  isTermValidating?: boolean
  onApplyFix?: (suggestedCql: string) => void
  aiEnabled?: boolean
}

export default function ElmViewer({ terminologyResults = [], isTermValidating = false, onApplyFix, aiEnabled = true }: ElmViewerProps) {
  const { t } = useTranslation('editor')
  const { elmJson, errors, warnings, cqlContent } = useSelector((state: RootState) => state.editor)
  const fixMutation = useFixSuggestion()
  const [activeSuggestion, setActiveSuggestion] = useState<{
    index: number
    explanation?: string
    suggestedCql?: string
    errorMessage?: string
  } | null>(null)
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
        <Tab label={t('elm.metadata')} {...a11yProps(0, 'elm')} />
        <Tab
          {...a11yProps(1, 'elm')}
          label={
            <Stack direction="row" spacing={1} alignItems="center">
              <span>{t('elm.errors')}</span>
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
              <span>{t('elm.warnings')}</span>
              {warnings.length > 0 && (
                <Chip label={warnings.length} color="warning" size="small" />
              )}
            </Stack>
          }
        />
        <Tab label={t('elm.elmJson')} {...a11yProps(3, 'elm')} />
        <Tab
          {...a11yProps(4, 'elm')}
          label={
            <Stack direction="row" spacing={1} alignItems="center">
              <span>{t('elm.terminology')}</span>
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
                {t('elm.library')}
              </Typography>
              <Typography sx={{ color: 'text.primary', fontWeight: 500 }}>
                {metadata.libraryId} v{metadata.version}
              </Typography>
            </Box>

            {metadata.usings.length > 0 && (
              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  {t('elm.using')}
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
                  {t('elm.includes')}
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
                  {t('elm.parameters')}
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
                  {t('elm.valueSets')}
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
                  {t('elm.expressions', { count: metadata.statements.length })}
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
            {t('elm.noElmData')}
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
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography variant="body2" fontWeight="bold" sx={{ color: 'error.dark' }}>
                      {t('elm.lineCol', { line: error.startLine, column: error.startColumn })}
                    </Typography>
                    <Typography variant="body2" sx={{ color: 'text.primary' }}>{error.message}</Typography>
                  </Box>
                  {aiEnabled && <Button
                    size="small"
                    startIcon={
                      fixMutation.isPending && activeSuggestion?.index === i
                        ? <CircularProgress size={14} />
                        : <AiFixIcon />
                    }
                    disabled={fixMutation.isPending}
                    onClick={() => {
                      setActiveSuggestion({ index: i })
                      fixMutation.mutate(
                        { cql: cqlContent, error },
                        {
                          onSuccess: (data) => {
                            if (data.success) {
                              setActiveSuggestion({
                                index: i,
                                explanation: data.explanation,
                                suggestedCql: data.suggestedCql,
                              })
                            } else {
                              setActiveSuggestion({
                                index: i,
                                errorMessage: data.errorMessage || t('elm.aiFixUnavailable'),
                              })
                            }
                          },
                          onError: () => {
                            setActiveSuggestion({
                              index: i,
                              errorMessage: t('elm.aiFixUnavailable'),
                            })
                          },
                        }
                      )
                    }}
                    sx={{
                      ml: 1,
                      flexShrink: 0,
                      textTransform: 'none',
                      color: 'primary.main',
                      borderColor: 'primary.main',
                    }}
                    variant="outlined"
                  >
                    {t('elm.aiFix')}
                  </Button>}
                </Stack>
                <Collapse in={activeSuggestion?.index === i && (!!activeSuggestion?.explanation || !!activeSuggestion?.suggestedCql || !!activeSuggestion?.errorMessage)}>
                  {activeSuggestion?.index === i && (
                    <Box sx={{ mt: 1.5, pt: 1.5, borderTop: '1px solid rgba(0,0,0,0.08)' }}>
                      {activeSuggestion.errorMessage ? (
                        <>
                          <Typography variant="body2" color="error.main" sx={{ mb: 1 }}>
                            {activeSuggestion.errorMessage}
                          </Typography>
                          <Button size="small" onClick={() => setActiveSuggestion(null)}>
                            {t('elm.dismiss')}
                          </Button>
                        </>
                      ) : (
                        <>
                          {activeSuggestion.explanation && (
                            <Box sx={{ mb: 1.5 }}>
                              <Typography variant="caption" fontWeight="bold" color="text.secondary">
                                {t('elm.aiExplanation')}
                              </Typography>
                              <Typography variant="body2" sx={{ mt: 0.5 }}>
                                {activeSuggestion.explanation}
                              </Typography>
                            </Box>
                          )}
                          {activeSuggestion.suggestedCql && (
                            <Box sx={{ mb: 1.5 }}>
                              <Typography variant="caption" fontWeight="bold" color="text.secondary">
                                {t('elm.suggestedFix')}
                              </Typography>
                              <Box
                                component="pre"
                                sx={{
                                  mt: 0.5,
                                  p: 1.5,
                                  bgcolor: 'action.hover',
                                  borderRadius: '6px',
                                  border: '1px solid',
                                  borderColor: 'divider',
                                  overflow: 'auto',
                                  fontSize: '0.75rem',
                                  maxHeight: 200,
                                  fontFamily: '"JetBrains Mono", "Fira Code", "Consolas", monospace',
                                  color: 'text.primary',
                                  whiteSpace: 'pre-wrap',
                                  wordBreak: 'break-word',
                                }}
                              >
                                {activeSuggestion.suggestedCql}
                              </Box>
                            </Box>
                          )}
                          <Stack direction="row" spacing={1}>
                            {activeSuggestion.suggestedCql && onApplyFix && (
                              <Button
                                size="small"
                                variant="contained"
                                onClick={() => {
                                  onApplyFix(activeSuggestion.suggestedCql!)
                                  setActiveSuggestion(null)
                                }}
                                sx={{
                                  textTransform: 'none',
                                  background: 'linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)',
                                }}
                              >
                                {t('elm.applyFix')}
                              </Button>
                            )}
                            <Button size="small" onClick={() => setActiveSuggestion(null)} sx={{ textTransform: 'none' }}>
                              {t('elm.dismiss')}
                            </Button>
                          </Stack>
                        </>
                      )}
                    </Box>
                  )}
                </Collapse>
              </Paper>
            ))}
          </Stack>
        ) : (
          <Typography color="success.main">{t('elm.noErrors')}</Typography>
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
                  {t('elm.lineCol', { line: warning.startLine, column: warning.startColumn })}
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.primary' }}>{warning.message}</Typography>
              </Paper>
            ))}
          </Stack>
        ) : (
          <Typography color="text.secondary">{t('elm.noWarnings')}</Typography>
        )}
      </TabPanel>

      <TabPanel value={tabValue} index={3} prefix="elm" sx={{ p: 2 }}>
        {elmJson ? (
          <Box
            component="pre"
            sx={{
              p: 2,
              bgcolor: 'action.hover',
              borderRadius: '8px',
              border: '1px solid',
              borderColor: 'divider',
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
            {t('elm.noElmJson')}
          </Typography>
        )}
      </TabPanel>

      <TabPanel value={tabValue} index={4} prefix="elm" sx={{ p: 2 }}>
        {isTermValidating ? (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CircularProgress size={20} />
            <Typography color="text.secondary">{t('elm.validatingTerminology')}</Typography>
          </Box>
        ) : terminologyResults.length > 0 ? (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell scope="col" sx={{ fontWeight: 600, width: 40 }}>{t('elm.terminologyStatus')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('elm.terminologyType')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('elm.terminologyName')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('elm.terminologyReference')}</TableCell>
                  <TableCell scope="col" sx={{ fontWeight: 600 }}>{t('elm.terminologyDetail')}</TableCell>
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
            {t('elm.noTerminology')}
          </Typography>
        )}
      </TabPanel>
    </Paper>
  )
}
