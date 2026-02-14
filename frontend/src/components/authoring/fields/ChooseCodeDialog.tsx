import { useState } from 'react'
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField,
  FormControl, InputLabel, Select, MenuItem, Stack, IconButton,
  Table, TableHead, TableBody, TableRow, TableCell, Typography, Box,
  Alert, CircularProgress, InputAdornment, Divider, Chip,
} from '@mui/material'
import { Close as CloseIcon, CheckCircle as ValidIcon, Search as SearchIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import { useLookupCode, useSearchCodes } from '../../../hooks/useTerminology'
import { ALL_CODE_SYSTEMS, findCodeSystemByUrl } from '../../../constants/codeSystems'
import type { CodeReference } from '../../../types/authoring'

interface ChooseCodeDialogProps {
  open: boolean
  onClose: () => void
  onSelect: (code: CodeReference) => void
}

export default function ChooseCodeDialog({ open, onClose, onSelect }: ChooseCodeDialogProps) {
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

  // Live search
  const { data: searchResults, isLoading: isSearching } = useSearchCodes(
    effectiveSystemUrl,
    searchText,
  )

  const handleSystemChange = (value: string) => {
    if (value === '__other__') {
      setIsOther(true)
      setSystemUrl('')
      setSystemLabel('Other')
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
        Choose code
        <IconButton onClick={handleClose} size="small" aria-label="Close dialog"><CloseIcon /></IconButton>
      </DialogTitle>

      <DialogContent>
        {/* Manual code entry */}
        <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ mt: 1 }}>
          <TextField
            label="Code"
            size="small"
            value={code}
            onChange={(e) => { setCode(e.target.value); setValidated(false) }}
            sx={{ flex: 1 }}
          />
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel>Code system</InputLabel>
            <Select
              value={isOther ? '__other__' : systemUrl}
              label="Code system"
              onChange={(e) => handleSystemChange(e.target.value)}
            >
              {ALL_CODE_SYSTEMS.map((cs) => (
                <MenuItem key={cs.url} value={cs.url}>{cs.label}</MenuItem>
              ))}
              <MenuItem value="__other__">Other</MenuItem>
            </Select>
          </FormControl>
          <GradientButton
            onClick={handleValidate}
            disabled={!code.trim() || !effectiveSystemUrl.trim() || lookupMutation.isPending}
            sx={{ minWidth: 100, height: 40 }}
          >
            {lookupMutation.isPending ? <CircularProgress size={18} color="inherit" /> : 'VALIDATE'}
          </GradientButton>
        </Stack>

        {isOther && (
          <TextField
            label="Code system URL"
            size="small"
            fullWidth
            value={customSystemUrl}
            onChange={(e) => { setCustomSystemUrl(e.target.value); setValidated(false) }}
            placeholder="e.g. http://example.org/fhir/CodeSystem/my-codes"
            sx={{ mt: 1 }}
          />
        )}

        {/* Search section */}
        {effectiveSystemUrl && (
          <>
            <Divider sx={{ my: 2 }}>
              <Chip label="OR SEARCH" size="small" />
            </Divider>

            <TextField
              fullWidth
              size="small"
              placeholder={`Search codes in ${systemLabel || 'selected system'}...`}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              InputProps={{
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
              }}
            />

            {searchResults && searchResults.length > 0 && (
              <Box sx={{ mt: 1, maxHeight: 200, overflow: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem' }}>CODE</TableCell>
                      <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem' }}>DISPLAY</TableCell>
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
                          <Typography variant="body2" fontWeight={600}>{r.code}</Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">{r.display}</Typography>
                        </TableCell>
                        <TableCell>
                          <Button size="small" variant="outlined">Use</Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}

            {searchText.length >= 2 && !isSearching && searchResults && searchResults.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
                No codes found for &quot;{searchText}&quot;
              </Typography>
            )}
          </>
        )}

        {/* Validation result */}
        {lookupMutation.isError && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            Could not look up display name. The code will be added with the code value as display.
          </Alert>
        )}

        {validated && validationResult && (
          <Box sx={{ mt: 3 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600, textTransform: 'uppercase', fontSize: '0.75rem' }}>Code</TableCell>
                  <TableCell sx={{ fontWeight: 600, textTransform: 'uppercase', fontSize: '0.75rem' }}>Code System</TableCell>
                  <TableCell sx={{ fontWeight: 600, textTransform: 'uppercase', fontSize: '0.75rem' }}>Display</TableCell>
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
                <Typography variant="body2" color="success.main" fontWeight={600}>
                  Validation Successful!
                </Typography>
              </Box>
            )}
          </Box>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose}>CANCEL</Button>
        <Button variant="contained" onClick={handleSelect} disabled={!validated || !validationResult}>
          SELECT
        </Button>
      </DialogActions>
    </Dialog>
  )
}
