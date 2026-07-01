import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box,
  TextField,
  Autocomplete,
  Typography,
  IconButton,
  Tooltip,
  Stack,
  Paper,
  Chip,
  Divider,
} from '@mui/material'
import {
  ContentCopy as CopyIcon,
  Check as CheckIcon,
  AddCircleOutlined as UseIcon,
  Search as SearchIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { ALL_CODE_SYSTEMS, type CodeSystemEntry } from '../../constants/codeSystems'
import { useLookupCode } from '../../hooks/useTerminology'
import { useCopyFeedback } from '../../hooks/useCopyFeedback'
import { extractApiError } from '../../utils/errorUtils'
import type { SelectedCoding } from '../../contexts/TerminologyDrawerContext'

const COPIED_KEY = 'lookup-result'

interface DrawerCodeLookupPanelProps {
  onSelect?: (coding: SelectedCoding) => void
}

export default function DrawerCodeLookupPanel({ onSelect }: DrawerCodeLookupPanelProps) {
  const { t } = useTranslation('terminology')
  const [system, setSystem] = useState('')
  const [code, setCode] = useState('')
  const { isCopied, markCopied } = useCopyFeedback()

  const lookupMutation = useLookupCode()

  const handleLookup = () => {
    if (!system || !code) return
    // Pass `system` verbatim. The previous `system.includes(cs.url)` heuristic
    // could pick the wrong CodeSystem when the user-entered URL happens to
    // contain a known CS URL as a substring (e.g. a wrapping proxy URL with
    // `http://snomed.info/sct` inside). Exact-match in the Autocomplete value
    // path already normalizes well-known systems.
    lookupMutation.mutate({ system, code })
  }

  const result = lookupMutation.data

  const handleCopy = async () => {
    if (!result) return
    await navigator.clipboard.writeText(`${result.system}|${result.code}`)
    markCopied(COPIED_KEY)
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
            label={t('codeLookup.systemLabel')}
            placeholder={t('codeLookup.systemPlaceholder')}
            size="small"
            fullWidth
          />
        )}
        size="small"
      />
      <TextField
        label={t('codeLookup.codeLabel')}
        placeholder={t('codeLookup.codePlaceholder')}
        size="small"
        fullWidth
        value={code}
        onChange={(e) => setCode(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && handleLookup()}
      />
      <GradientButton
        startIcon={<SearchIcon />}
        onClick={handleLookup}
        disabled={!system || !code || lookupMutation.isPending}
        fullWidth
      >
        {lookupMutation.isPending ? t('codeLookup.lookingUp') : t('codeLookup.lookupCode')}
      </GradientButton>
      {lookupMutation.isError && (
        <Typography variant="body2" color="error">
          {t('codeLookup.lookupFailed', { error: extractApiError(lookupMutation.error) })}
        </Typography>
      )}
      {result && (
        <Paper variant="outlined" sx={{ p: 1.5, flex: 1, overflow: 'auto' }}>
          <Stack
            direction="row"
            sx={{
              justifyContent: "space-between",
              alignItems: "center",
              mb: 1
            }}>
            <Typography variant="subtitle2">{t('codeLookup.codeDetails')}</Typography>
            <Stack direction="row" spacing={0.5}>
              <Tooltip title={isCopied(COPIED_KEY) ? t('drawer.copied') : t('drawer.copyTooltip')}>
                <IconButton size="small" onClick={handleCopy}>
                  {isCopied(COPIED_KEY) ? <CheckIcon sx={{ fontSize: 16 }} /> : <CopyIcon sx={{ fontSize: 16 }} />}
                </IconButton>
              </Tooltip>
              {onSelect && (
                <Tooltip title={t('drawer.useCodeTooltip')}>
                  <IconButton
                    size="small"
                    color="primary"
                    onClick={() => onSelect({
                      system: result.system,
                      code: result.code,
                      display: result.display,
                    })}
                  >
                    <UseIcon sx={{ fontSize: 16 }} />
                  </IconButton>
                </Tooltip>
              )}
            </Stack>
          </Stack>
          <Divider sx={{ mb: 1 }} />
          <Stack spacing={0.75}>
            <Box>
              <Typography variant="caption" sx={{
                color: "text.secondary"
              }}>{t('codeLookup.detailSystem')}</Typography>
              <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{result.system}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" sx={{
                color: "text.secondary"
              }}>{t('codeLookup.detailCode')}</Typography>
              <Typography variant="body2" sx={{
                fontWeight: 600
              }}>{result.code}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" sx={{
                color: "text.secondary"
              }}>{t('codeLookup.detailDisplay')}</Typography>
              <Typography variant="body2">{result.display}</Typography>
            </Box>
            {result.name && (
              <Box>
                <Typography variant="caption" sx={{
                  color: "text.secondary"
                }}>{t('codeLookup.detailName')}</Typography>
                <Typography variant="body2">{result.name}</Typography>
              </Box>
            )}
            {result.designations && result.designations.length > 0 && (
              <Box>
                <Typography variant="caption" sx={{
                  color: "text.secondary"
                }}>{t('codeLookup.detailDesignations')}</Typography>
                <Stack
                  direction="row"
                  spacing={0.5}
                  useFlexGap
                  sx={{
                    flexWrap: "wrap",
                    mt: 0.25
                  }}>
                  {result.designations.map((d, i) => (
                    <Chip key={i} label={d} size="small" sx={{ height: 22, fontSize: '0.7rem' }} />
                  ))}
                </Stack>
              </Box>
            )}
          </Stack>
        </Paper>
      )}
    </Box>
  );
}
