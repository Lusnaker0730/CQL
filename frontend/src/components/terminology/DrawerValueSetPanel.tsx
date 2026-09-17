import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Alert,
  Box,
  TextField,
  Typography,
  IconButton,
  Tooltip,
  Stack,
  CircularProgress,
  List,
  ListItemButton,
  ListItemText,
  Collapse,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from '@mui/material'
import {
  ContentCopy as CopyIcon,
  Check as CheckIcon,
  AddCircleOutlined as UseIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
} from '@mui/icons-material'
import { useSearchValueSets, useExpandValueSet } from '../../hooks/useTerminology'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useCopyFeedback } from '../../hooks/useCopyFeedback'
import { extractApiError } from '../../utils/errorUtils'
import { SEARCH_DEBOUNCE_CODE_MS } from '../../constants/timing'
import type { SelectedCoding } from '../../contexts/TerminologyDrawerContext'

interface DrawerValueSetPanelProps {
  onSelect?: (coding: SelectedCoding) => void
}

const PREVIEW_CODE_LIMIT = 50

export default function DrawerValueSetPanel({ onSelect }: DrawerValueSetPanelProps) {
  const { t } = useTranslation('terminology')
  const [search, setSearch] = useState('')
  const [expandedUrl, setExpandedUrl] = useState<string | null>(null)
  const { isCopied, markCopied } = useCopyFeedback()

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_CODE_MS)
  const { data: valueSets = [], isLoading } = useSearchValueSets(debouncedSearch)
  const expandMutation = useExpandValueSet()

  // Race-guard: a single expandMutation is shared across rows. Only render
  // results when the variables.url that produced them matches the row the
  // user is currently looking at — otherwise switching rows mid-flight could
  // briefly show A's codes inside B's collapse panel.
  const expandedRequestUrl = expandMutation.variables?.url
  const showExpansion = expandedUrl !== null && expandedRequestUrl === expandedUrl
  const expandedCodes = showExpansion ? (expandMutation.data?.expansion?.contains || []) : []
  const expandError = showExpansion && expandMutation.isError
    ? extractApiError(expandMutation.error)
    : null
  const previewCodes = expandedCodes.slice(0, PREVIEW_CODE_LIMIT)
  const isTruncated = expandedCodes.length > PREVIEW_CODE_LIMIT

  const handleToggle = (url: string) => {
    if (expandedUrl === url) {
      setExpandedUrl(null)
      return
    }
    setExpandedUrl(url)
    // Surface failures via the in-panel Alert (catch is handled by the
    // mutation hook's error state, no need to await/try here).
    expandMutation.mutate({ url })
  }

  const handleCopy = async (coding: SelectedCoding) => {
    await navigator.clipboard.writeText(`${coding.system}|${coding.code}`)
    markCopied(coding.code)
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, height: '100%' }}>
      <TextField
        label={t('valueSet.searchLabel')}
        placeholder={t('valueSet.searchPlaceholder')}
        size="small"
        fullWidth
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size={24} />
          </Box>
        ) : valueSets.length === 0 && search.length >= 2 ? (
          <Typography
            variant="body2"
            sx={{
              color: "text.secondary",
              py: 2,
              textAlign: 'center'
            }}>
            {t('valueSet.noResults')}
          </Typography>
        ) : (
          <List dense disablePadding>
            {valueSets.map((vs) => (
              <Box key={vs.url}>
                <ListItemButton onClick={() => handleToggle(vs.url)} sx={{ py: 0.5 }}>
                  {expandedUrl === vs.url ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                  <ListItemText
                    primary={
                      <Typography variant="body2" noWrap sx={{
                        fontWeight: 500
                      }}>
                        {vs.title || vs.name}
                      </Typography>
                    }
                    secondary={
                      <Typography variant="caption" noWrap sx={{
                        color: "text.secondary"
                      }}>
                        {vs.url}
                      </Typography>
                    }
                    sx={{ ml: 0.5 }}
                  />
                </ListItemButton>
                <Collapse in={expandedUrl === vs.url} timeout="auto" unmountOnExit>
                  <Box sx={{ pl: 4, pr: 1, pb: 1 }}>
                    {expandMutation.isPending && expandedRequestUrl === vs.url ? (
                      <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
                        <CircularProgress size={20} />
                      </Box>
                    ) : expandError ? (
                      <Alert severity="error" sx={{ fontSize: '0.75rem', py: 0.5 }}>
                        {t('valueSet.expandFailed', { error: expandError })}
                      </Alert>
                    ) : expandedCodes.length === 0 ? (
                      <Typography variant="caption" sx={{
                        color: "text.secondary"
                      }}>
                        {t('valueSet.noCodesFound')}
                      </Typography>
                    ) : (
                      <>
                        <Stack
                          direction="row"
                          spacing={0.5}
                          sx={{
                            alignItems: "center",
                            mb: 0.5
                          }}>
                          <Chip
                            label={t('valueSet.resultCount', { count: expandedCodes.length })}
                            size="small"
                            sx={{ height: 20, fontSize: '0.65rem' }}
                          />
                          {isTruncated && (
                            <Chip
                              label={t('valueSet.previewTruncated', { shown: PREVIEW_CODE_LIMIT, total: expandedCodes.length })}
                              size="small"
                              variant="outlined"
                              color="warning"
                              sx={{ height: 20, fontSize: '0.65rem' }}
                            />
                          )}
                        </Stack>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell sx={{ fontWeight: 600, py: 0.25, fontSize: '0.7rem' }}>
                                {t('valueSet.colCode')}
                              </TableCell>
                              <TableCell sx={{ fontWeight: 600, py: 0.25, fontSize: '0.7rem' }}>
                                {t('valueSet.colDisplay')}
                              </TableCell>
                              <TableCell sx={{ py: 0.25, width: 64 }} />
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {previewCodes.map((code) => (
                              <TableRow key={`${code.system}-${code.code}`} hover>
                                <TableCell sx={{ py: 0.25, fontSize: '0.75rem', fontFamily: 'monospace' }}>
                                  {code.code}
                                </TableCell>
                                <TableCell sx={{ py: 0.25, fontSize: '0.75rem' }}>
                                  {code.display}
                                </TableCell>
                                <TableCell sx={{ py: 0.25 }}>
                                  <Stack direction="row" spacing={0}>
                                    <Tooltip title={isCopied(code.code) ? t('drawer.copied') : t('drawer.copyTooltip')}>
                                      <IconButton
                                        size="small"
                                        onClick={() => handleCopy({ system: code.system, code: code.code, display: code.display })}
                                        aria-label={t('drawer.copyTooltip')}
                                      >
                                        {isCopied(code.code) ? <CheckIcon sx={{ fontSize: 14 }} /> : <CopyIcon sx={{ fontSize: 14 }} />}
                                      </IconButton>
                                    </Tooltip>
                                    {onSelect && (
                                      <Tooltip title={t('drawer.useCodeTooltip')}>
                                        <IconButton
                                          size="small"
                                          color="primary"
                                          onClick={() => onSelect({ system: code.system, code: code.code, display: code.display })}
                                          aria-label={t('drawer.useCodeTooltip')}
                                        >
                                          <UseIcon sx={{ fontSize: 14 }} />
                                        </IconButton>
                                      </Tooltip>
                                    )}
                                  </Stack>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </>
                    )}
                  </Box>
                </Collapse>
              </Box>
            ))}
          </List>
        )}
      </Box>
    </Box>
  );
}
