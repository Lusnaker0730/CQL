import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  TextField,
  Autocomplete,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  IconButton,
  Tooltip,
  Chip,
  Stack,
  CircularProgress,
} from '@mui/material'
import {
  ContentCopy as CopyIcon,
  Check as CheckIcon,
  AddCircleOutline as UseIcon,
} from '@mui/icons-material'
import { ALL_CODE_SYSTEMS, type CodeSystemEntry } from '../../constants/codeSystems'
import { useSearchCodes } from '../../hooks/useTerminology'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useCopyFeedback } from '../../hooks/useCopyFeedback'
import { SEARCH_DEBOUNCE_CODE_MS } from '../../constants/timing'
import type { SelectedCoding } from '../../contexts/TerminologyDrawerContext'

interface DrawerCodeSearchPanelProps {
  initialSystem?: string
  initialSearchText?: string
  onSelect?: (coding: SelectedCoding) => void
}

export default function DrawerCodeSearchPanel({
  initialSystem,
  initialSearchText,
  onSelect,
}: DrawerCodeSearchPanelProps) {
  const { t } = useTranslation('terminology')
  const [system, setSystem] = useState(initialSystem || '')
  const [searchText, setSearchText] = useState(initialSearchText || '')
  const { isCopied, markCopied } = useCopyFeedback()

  // Apply initial values when they change (e.g. opened from a field)
  useEffect(() => {
    if (initialSystem) setSystem(initialSystem)
    if (initialSearchText) setSearchText(initialSearchText)
  }, [initialSystem, initialSearchText])

  const debouncedSearchText = useDebouncedValue(searchText, SEARCH_DEBOUNCE_CODE_MS)
  const { data: results = [], isLoading } = useSearchCodes(system, debouncedSearchText)

  const handleCopy = async (coding: SelectedCoding) => {
    await navigator.clipboard.writeText(`${coding.system}|${coding.code}`)
    markCopied(coding.code)
  }

  const selectedEntry = ALL_CODE_SYSTEMS.find((cs) => cs.url === system) || null

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, height: '100%' }}>
      <Autocomplete
        freeSolo
        options={ALL_CODE_SYSTEMS}
        getOptionLabel={(opt) =>
          typeof opt === 'string' ? opt : `${opt.label} — ${opt.url}`
        }
        value={selectedEntry}
        inputValue={system}
        onInputChange={(_, v, reason) => {
          if (reason === 'input' || reason === 'clear') setSystem(v)
        }}
        onChange={(_, newVal) => {
          if (typeof newVal === 'string') setSystem(newVal)
          else if (newVal) setSystem((newVal as CodeSystemEntry).url)
          else setSystem('')
        }}
        renderInput={(params) => (
          <TextField
            {...params}
            label={t('drawer.systemPlaceholder')}
            size="small"
            fullWidth
          />
        )}
        size="small"
      />

      <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
        <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5, alignSelf: 'center' }}>
          {t('drawer.quickSystems')}:
        </Typography>
        {ALL_CODE_SYSTEMS.slice(0, 6).map((cs) => (
          <Chip
            key={cs.url}
            label={cs.label}
            size="small"
            variant={system === cs.url ? 'filled' : 'outlined'}
            color={system === cs.url ? 'primary' : 'default'}
            onClick={() => setSystem(cs.url)}
            sx={{ height: 22, fontSize: '0.7rem' }}
          />
        ))}
      </Stack>

      <TextField
        label={t('drawer.searchPlaceholder')}
        size="small"
        fullWidth
        value={searchText}
        onChange={(e) => setSearchText(e.target.value)}
      />

      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size={24} />
          </Box>
        ) : results.length === 0 && searchText.length >= 2 && system ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
            {t('drawer.noResults')}
          </Typography>
        ) : (
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600, py: 0.5, fontSize: '0.75rem' }}>
                  {t('codeLookup.colCode')}
                </TableCell>
                <TableCell sx={{ fontWeight: 600, py: 0.5, fontSize: '0.75rem' }}>
                  {t('codeLookup.colDisplay')}
                </TableCell>
                <TableCell sx={{ fontWeight: 600, py: 0.5, fontSize: '0.75rem', width: 72 }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {results.map((r) => (
                <TableRow key={`${r.system}-${r.code}`} hover>
                  <TableCell sx={{ py: 0.5, fontSize: '0.8rem', fontFamily: 'monospace' }}>
                    {r.code}
                  </TableCell>
                  <TableCell sx={{ py: 0.5, fontSize: '0.8rem' }}>{r.display}</TableCell>
                  <TableCell sx={{ py: 0.5 }}>
                    <Stack direction="row" spacing={0}>
                      <Tooltip title={isCopied(r.code) ? t('drawer.copied') : t('drawer.copyTooltip')}>
                        <IconButton
                          size="small"
                          onClick={() => handleCopy({ system: r.system, code: r.code, display: r.display })}
                          aria-label={t('drawer.copyTooltip')}
                        >
                          {isCopied(r.code) ? <CheckIcon sx={{ fontSize: 16 }} /> : <CopyIcon sx={{ fontSize: 16 }} />}
                        </IconButton>
                      </Tooltip>
                      {onSelect && (
                        <Tooltip title={t('drawer.useCodeTooltip')}>
                          <IconButton
                            size="small"
                            color="primary"
                            onClick={() => onSelect({ system: r.system, code: r.code, display: r.display })}
                          >
                            <UseIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Box>
    </Box>
  )
}
