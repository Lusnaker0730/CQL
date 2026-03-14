import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
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
  AddCircleOutline as UseIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
} from '@mui/icons-material'
import { useSearchValueSets, useExpandValueSet } from '../../hooks/useTerminology'
import { COPY_FEEDBACK_TIMEOUT_MS } from '../../constants/timing'
import type { SelectedCoding } from '../../contexts/TerminologyDrawerContext'
import type { ValueSetCode } from '../../types'

interface DrawerValueSetPanelProps {
  onSelect?: (coding: SelectedCoding) => void
}

export default function DrawerValueSetPanel({ onSelect }: DrawerValueSetPanelProps) {
  const { t } = useTranslation('terminology')
  const [search, setSearch] = useState('')
  const [expandedUrl, setExpandedUrl] = useState<string | null>(null)
  const [expandedCodes, setExpandedCodes] = useState<ValueSetCode[]>([])
  const [copiedCode, setCopiedCode] = useState<string | null>(null)

  const { data: valueSets = [], isLoading } = useSearchValueSets(search)
  const expandMutation = useExpandValueSet()

  const handleToggle = async (url: string) => {
    if (expandedUrl === url) {
      setExpandedUrl(null)
      return
    }
    setExpandedUrl(url)
    setExpandedCodes([])
    try {
      const result = await expandMutation.mutateAsync({ url })
      setExpandedCodes(result.expansion?.contains || [])
    } catch {
      // expansion failed
    }
  }

  const handleCopy = async (coding: SelectedCoding) => {
    await navigator.clipboard.writeText(`${coding.system}|${coding.code}`)
    setCopiedCode(coding.code)
    setTimeout(() => setCopiedCode(null), COPY_FEEDBACK_TIMEOUT_MS)
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
          <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
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
                      <Typography variant="body2" fontWeight={500} noWrap>
                        {vs.title || vs.name}
                      </Typography>
                    }
                    secondary={
                      <Typography variant="caption" color="text.secondary" noWrap>
                        {vs.url}
                      </Typography>
                    }
                    sx={{ ml: 0.5 }}
                  />
                </ListItemButton>
                <Collapse in={expandedUrl === vs.url} timeout="auto" unmountOnExit>
                  <Box sx={{ pl: 4, pr: 1, pb: 1 }}>
                    {expandMutation.isPending && expandedUrl === vs.url ? (
                      <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
                        <CircularProgress size={20} />
                      </Box>
                    ) : expandedCodes.length === 0 ? (
                      <Typography variant="caption" color="text.secondary">
                        {t('valueSet.noCodesFound')}
                      </Typography>
                    ) : (
                      <>
                        <Chip
                          label={t('valueSet.resultCount', { count: expandedCodes.length })}
                          size="small"
                          sx={{ mb: 0.5, height: 20, fontSize: '0.65rem' }}
                        />
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
                            {expandedCodes.slice(0, 50).map((code) => (
                              <TableRow key={`${code.system}-${code.code}`} hover>
                                <TableCell sx={{ py: 0.25, fontSize: '0.75rem', fontFamily: 'monospace' }}>
                                  {code.code}
                                </TableCell>
                                <TableCell sx={{ py: 0.25, fontSize: '0.75rem' }}>
                                  {code.display}
                                </TableCell>
                                <TableCell sx={{ py: 0.25 }}>
                                  <Stack direction="row" spacing={0}>
                                    <Tooltip title={copiedCode === code.code ? t('drawer.copied') : t('drawer.copyTooltip')}>
                                      <IconButton
                                        size="small"
                                        onClick={() => handleCopy({ system: code.system, code: code.code, display: code.display })}
                                      >
                                        {copiedCode === code.code ? <CheckIcon sx={{ fontSize: 14 }} /> : <CopyIcon sx={{ fontSize: 14 }} />}
                                      </IconButton>
                                    </Tooltip>
                                    {onSelect && (
                                      <Tooltip title={t('drawer.useCodeTooltip')}>
                                        <IconButton
                                          size="small"
                                          color="primary"
                                          onClick={() => onSelect({ system: code.system, code: code.code, display: code.display })}
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
  )
}
