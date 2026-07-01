import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField,
  FormControl, InputLabel, Select, MenuItem, Stack, IconButton,
  Table, TableHead, TableBody, TableRow, TableCell, Typography, Box,
  Alert, CircularProgress, InputAdornment, Divider, Chip,
} from '@mui/material'
import { Close as CloseIcon, CheckCircle as ValidIcon, Search as SearchIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import { useLookupCode, useSearchCodes } from '../../../hooks/useTerminology'
import { ALL_CODE_SYSTEMS, findCodeSystemByUrl, RESOURCE_SUGGESTED_CODES } from '../../../constants/codeSystems'
import type { CodeReference } from '../../../types/authoring'

interface ChooseCodeDialogProps {
  open: boolean
  onClose: () => void
  onSelect: (code: CodeReference) => void
  resourceType?: string
}

export default function ChooseCodeDialog({ open, onClose, onSelect, resourceType }: ChooseCodeDialogProps) {
  const { t } = useTranslation('authoring')
  const [code, setCode] = useState('')
  const [systemUrl, setSystemUrl] = useState('')
  const [systemLabel, setSystemLabel] = useState('')
  const [customSystemUrl, setCustomSystemUrl] = useState('')
  const [isOther, setIsOther] = useState(false)
  const [searchText, setSearchText] = useState('')
  const [validated, setValidated] = useState(false)
  const [validationResult, setValidationResult] = useState<{
    code: string
    codeSystem: string
    display: string
  } | null>(null)

  const lookupMutation = useLookupCode()
  const effectiveSystemUrl = isOther ? customSystemUrl : systemUrl
  const suggestedCodes = resourceType ? RESOURCE_SUGGESTED_CODES[resourceType] : undefined

  const handleQuickSelect = (entry: { code: string; display: string; displayZh: string; system: string; systemLabel: string }) => {
    setCode(entry.code)
    setSystemUrl(entry.system)
    setSystemLabel(entry.systemLabel)
    setIsOther(false)
    setValidated(true)
    setValidationResult({
      code: entry.code,
      codeSystem: entry.systemLabel,
      display: `${entry.display} (${entry.displayZh})`,
    })
  }

  // Live search
  const { data: searchResults, isLoading: isSearching } = useSearchCodes(
    effectiveSystemUrl,
    searchText,
  )

  const handleSystemChange = (value: string) => {
    if (value === '__other__') {
      setIsOther(true)
      setSystemUrl('')
      setSystemLabel(t('chooseCode.other'))
    } else {
      setIsOther(false)
      setSystemUrl(value)
      const match = findCodeSystemByUrl(value)
      setSystemLabel(match?.label || '')
    }
    setValidated(false)
    setValidationResult(null)
    setSearchText('')
  }

  const handleValidate = () => {
    if (!code.trim() || !effectiveSystemUrl.trim()) return
    lookupMutation.mutate(
      { system: effectiveSystemUrl, code: code.trim() },
      {
        onSuccess: (data) => {
          setValidated(true)
          setValidationResult({
            code: data.code,
            codeSystem: systemLabel || effectiveSystemUrl,
            display: data.display || data.name || code.trim(),
          })
        },
        onError: () => {
          setValidated(true)
          setValidationResult({
            code: code.trim(),
            codeSystem: systemLabel || effectiveSystemUrl,
            display: code.trim(),
          })
        },
      }
    )
  }

  const handleSelectFromSearch = (result: { system: string; code: string; display: string }) => {
    const csLabel = findCodeSystemByUrl(result.system)?.label || systemLabel || result.system
    setValidated(true)
    setValidationResult({
      code: result.code,
      codeSystem: csLabel,
      display: result.display || result.code,
    })
    setCode(result.code)
  }

  const handleSelect = () => {
    if (!validationResult) return
    onSelect({
      code: validationResult.code,
      codeSystem: {
        name: validationResult.codeSystem,
        id: effectiveSystemUrl,
      },
      display: validationResult.display,
    })
    handleReset()
    onClose()
  }

  const handleReset = () => {
    setCode('')
    setSystemUrl('')
    setSystemLabel('')
    setCustomSystemUrl('')
    setIsOther(false)
    setSearchText('')
    setValidated(false)
    setValidationResult(null)
    lookupMutation.reset()
  }

  const handleClose = () => {
    handleReset()
    onClose()
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {t('chooseCode.title')}
        <IconButton onClick={handleClose} size="small" aria-label="Close dialog"><CloseIcon /></IconButton>
      </DialogTitle>
      <DialogContent>
        {/* Quick select for resource-specific codes */}
        {suggestedCodes && suggestedCodes.length > 0 && (
          <>
            <Typography
              variant="subtitle2"
              sx={{
                color: "text.secondary",
                mt: 1,
                mb: 1
              }}>
              {t('chooseCode.quickSelect')}
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 1 }}>
              {suggestedCodes.map((sc) => (
                <Chip
                  key={sc.code}
                  label={`${sc.code} ${sc.displayZh}`}
                  onClick={() => handleQuickSelect(sc)}
                  color={validationResult?.code === sc.code ? 'primary' : 'default'}
                  variant={validationResult?.code === sc.code ? 'filled' : 'outlined'}
                  clickable
                />
              ))}
            </Box>
            <Divider sx={{ my: 1.5 }}>
              <Chip label={t('chooseCode.orManual')} size="small" />
            </Divider>
          </>
        )}

        {/* Manual code entry */}
        <Stack
          direction="row"
          spacing={1}
          sx={{
            alignItems: "flex-start",
            mt: 1
          }}>
          <TextField
            label={t('chooseCode.codeLabel')}
            size="small"
            value={code}
            onChange={(e) => { setCode(e.target.value); setValidated(false) }}
            sx={{ flex: 1 }}
          />
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel>{t('chooseCode.codeSystemLabel')}</InputLabel>
            <Select
              value={isOther ? '__other__' : systemUrl}
              label={t('chooseCode.codeSystemLabel')}
              onChange={(e) => handleSystemChange(e.target.value)}
            >
              {ALL_CODE_SYSTEMS.map((cs) => (
                <MenuItem key={cs.url} value={cs.url}>{cs.label}</MenuItem>
              ))}
              <MenuItem value="__other__">{t('chooseCode.other')}</MenuItem>
            </Select>
          </FormControl>
          <GradientButton
            onClick={handleValidate}
            disabled={!code.trim() || !effectiveSystemUrl.trim() || lookupMutation.isPending}
            sx={{ minWidth: 100, height: 40 }}
          >
            {lookupMutation.isPending ? <CircularProgress size={18} color="inherit" /> : t('chooseCode.validate')}
          </GradientButton>
        </Stack>

        {isOther && (
          <TextField
            label={t('chooseCode.codeSystemUrl')}
            size="small"
            fullWidth
            value={customSystemUrl}
            onChange={(e) => { setCustomSystemUrl(e.target.value); setValidated(false) }}
            placeholder={t('chooseCode.codeSystemPlaceholder')}
            sx={{ mt: 1 }}
          />
        )}

        {/* Search section */}
        {effectiveSystemUrl && (
          <>
            <Divider sx={{ my: 2 }}>
              <Chip label={t('chooseCode.orSearch')} size="small" />
            </Divider>

            <TextField
              fullWidth
              size="small"
              placeholder={t('chooseCode.searchCodesPlaceholder', { system: systemLabel || t('chooseCode.selectedSystemFallback') })}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" />
                    </InputAdornment>
                  ),
                  endAdornment: isSearching ? (
                    <InputAdornment position="end">
                      <CircularProgress size={18} />
                    </InputAdornment>
                  ) : null,
                }
              }}
            />

            {searchResults && searchResults.length > 0 && (
              <Box sx={{ mt: 1, maxHeight: 200, overflow: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem' }}>{t('chooseCode.colCode')}</TableCell>
                      <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem' }}>{t('chooseCode.colDisplay')}</TableCell>
                      <TableCell />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {searchResults.map((r) => (
                      <TableRow
                        key={`${r.system}-${r.code}`}
                        hover
                        sx={{
                          cursor: 'pointer',
                          backgroundColor: validationResult?.code === r.code ? 'action.selected' : undefined,
                        }}
                        onClick={() => handleSelectFromSearch(r)}
                      >
                        <TableCell>
                          <Typography variant="body2" sx={{
                            fontWeight: 600
                          }}>{r.code}</Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">{r.display}</Typography>
                        </TableCell>
                        <TableCell>
                          <Button size="small" variant="outlined">{t('chooseCode.use')}</Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}

            {searchText.length >= 2 && !isSearching && searchResults && searchResults.length === 0 && (
              <Typography
                variant="body2"
                sx={{
                  color: "text.secondary",
                  textAlign: 'center',
                  py: 2
                }}>
                {t('chooseCode.noCodesFound', { text: searchText })}
              </Typography>
            )}
          </>
        )}

        {/* Validation result */}
        {lookupMutation.isError && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            {t('chooseCode.lookupWarning')}
          </Alert>
        )}

        {validated && validationResult && (
          <Box sx={{ mt: 3 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600, textTransform: 'uppercase', fontSize: '0.75rem' }}>{t('chooseCode.colCodeHeader')}</TableCell>
                  <TableCell sx={{ fontWeight: 600, textTransform: 'uppercase', fontSize: '0.75rem' }}>{t('chooseCode.colCodeSystem')}</TableCell>
                  <TableCell sx={{ fontWeight: 600, textTransform: 'uppercase', fontSize: '0.75rem' }}>{t('chooseCode.colDisplayHeader')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                <TableRow>
                  <TableCell>{validationResult.code}</TableCell>
                  <TableCell>{validationResult.codeSystem}</TableCell>
                  <TableCell>{validationResult.display}</TableCell>
                </TableRow>
              </TableBody>
            </Table>

            {!lookupMutation.isError && (
              <Box sx={{ mt: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                <ValidIcon color="success" fontSize="small" />
                <Typography
                  variant="body2"
                  sx={{
                    color: "success.main",
                    fontWeight: 600
                  }}>
                  {t('chooseCode.validationSuccess')}
                </Typography>
              </Box>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('actions.cancel', { ns: 'common' })}</Button>
        <Button variant="contained" onClick={handleSelect} disabled={!validated || !validationResult}>
          {t('chooseCode.selectButton')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
