import { useState } from 'react'
import {
  Stack,
  TextField,
  Alert,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  CircularProgress,
  Paper,
  Box,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material'
import {
  ExpandMore as ExpandMoreIcon,
  Search as SearchIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import GradientButton from '../common/GradientButton'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import { extractApiError } from '../../utils/errorUtils'

export default function TerminologyTab() {
  const { t } = useTranslation('fhir')
  const { t: tc } = useTranslation('common')

  // ValueSet Expand
  const [vsUrl, setVsUrl] = useState('')
  const [vsFilter, setVsFilter] = useState('')

  // Code Lookup
  const [lookupSystem, setLookupSystem] = useState('')
  const [lookupCode, setLookupCode] = useState('')

  // Code Search
  const [searchSystem, setSearchSystem] = useState('')
  const [searchText, setSearchText] = useState('')

  const expandMutation = useMutation({
    mutationFn: () => fhirApi.expandValueSet(vsUrl, vsFilter || undefined),
  })

  const lookupMutation = useMutation({
    mutationFn: () => fhirApi.lookupCode(lookupSystem, lookupCode),
  })

  const searchMutation = useMutation({
    mutationFn: () => fhirApi.searchCodes(searchSystem, searchText),
  })

  const expandCodes = expandMutation.data?.expansion?.contains || []
  const lookupData = lookupMutation.data
  const searchResults = searchMutation.data || []

  return (
    <Stack spacing={2}>
      <Alert severity="info" sx={{ '& .MuiAlert-message': { fontSize: '0.8rem' } }}>
        {t('terminology.info')}
      </Alert>

      {/* ValueSet Expand */}
      <Accordion defaultExpanded>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">{t('terminology.vsExpand')}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Stack spacing={1.5}>
            <TextField
              label={t('terminology.vsUrlLabel')}
              value={vsUrl}
              onChange={(e) => setVsUrl(e.target.value)}
              size="small"
              fullWidth
              placeholder={t('terminology.vsUrlPlaceholder')}
            />
            <TextField
              label={t('terminology.filterLabel')}
              value={vsFilter}
              onChange={(e) => setVsFilter(e.target.value)}
              size="small"
              fullWidth
              placeholder={t('terminology.filterPlaceholder')}
            />
            <GradientButton
              onClick={() => expandMutation.mutate()}
              disabled={expandMutation.isPending || !vsUrl}
              startIcon={expandMutation.isPending ? <CircularProgress size={16} color="inherit" /> : <SearchIcon />}
              sx={{ alignSelf: 'flex-start', '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
            >
              {t('terminology.expand')}
            </GradientButton>
            {expandMutation.isError && (
              <Alert severity="error" sx={{ fontSize: '0.8rem' }}>
                {extractApiError(expandMutation.error)}
              </Alert>
            )}
            {expandCodes.length > 0 && (
              <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 300 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600 }}>{t('terminology.colSystem')}</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>{t('terminology.colCode')}</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>{t('terminology.colDisplay')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {expandCodes.map((c, idx) => (
                      <TableRow key={idx}>
                        <TableCell sx={{ fontSize: '0.75rem', fontFamily: 'monospace' }}>{c.system || ''}</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>{c.code || ''}</TableCell>
                        <TableCell sx={{ fontSize: '0.8rem' }}>{c.display || ''}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Stack>
        </AccordionDetails>
      </Accordion>

      {/* Code Lookup */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">{t('terminology.codeLookup')}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Stack spacing={1.5}>
            <TextField
              label={t('terminology.systemLabel')}
              value={lookupSystem}
              onChange={(e) => setLookupSystem(e.target.value)}
              size="small"
              fullWidth
              placeholder={t('terminology.systemPlaceholder')}
            />
            <TextField
              label={t('terminology.codeLabel')}
              value={lookupCode}
              onChange={(e) => setLookupCode(e.target.value)}
              size="small"
              fullWidth
              placeholder={t('terminology.codePlaceholder')}
            />
            <GradientButton
              onClick={() => lookupMutation.mutate()}
              disabled={lookupMutation.isPending || !lookupSystem || !lookupCode}
              startIcon={lookupMutation.isPending ? <CircularProgress size={16} color="inherit" /> : <SearchIcon />}
              sx={{ alignSelf: 'flex-start', '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
            >
              {t('terminology.lookup')}
            </GradientButton>
            {lookupMutation.isError && (
              <Alert severity="error" sx={{ fontSize: '0.8rem' }}>
                {extractApiError(lookupMutation.error)}
              </Alert>
            )}
            {lookupData && (
              <Box
                sx={{
                  p: 2,
                  bgcolor: 'action.hover',
                  borderRadius: '8px',
                  border: '1px solid',
                  borderColor: 'divider',
                }}
              >
                <Typography variant="body2"><strong>{t('terminology.displayLabel')}</strong> {lookupData.display || '-'}</Typography>
                {lookupData.designations && lookupData.designations.length > 0 && (
                  <Box sx={{ mt: 1 }}>
                    <Typography variant="caption" color="text.secondary">{t('terminology.designations')}</Typography>
                    {lookupData.designations.map((d, idx) => (
                      <Typography key={idx} variant="body2" sx={{ ml: 1, fontSize: '0.8rem' }}>
                        {d}
                      </Typography>
                    ))}
                  </Box>
                )}
              </Box>
            )}
          </Stack>
        </AccordionDetails>
      </Accordion>

      {/* Code Search */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">{t('terminology.codeSearch')}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Stack spacing={1.5}>
            <TextField
              label={t('terminology.systemLabel')}
              value={searchSystem}
              onChange={(e) => setSearchSystem(e.target.value)}
              size="small"
              fullWidth
              placeholder={t('terminology.systemPlaceholder')}
            />
            <TextField
              label={t('terminology.searchTextLabel')}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              size="small"
              fullWidth
              placeholder={t('terminology.searchTextPlaceholder')}
            />
            <GradientButton
              onClick={() => searchMutation.mutate()}
              disabled={searchMutation.isPending || !searchSystem || !searchText}
              startIcon={searchMutation.isPending ? <CircularProgress size={16} color="inherit" /> : <SearchIcon />}
              sx={{ alignSelf: 'flex-start', '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
            >
              {tc('actions.search')}
            </GradientButton>
            {searchMutation.isError && (
              <Alert severity="error" sx={{ fontSize: '0.8rem' }}>
                {extractApiError(searchMutation.error)}
              </Alert>
            )}
            {searchResults.length > 0 && (
              <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 300 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600 }}>{t('terminology.colCode')}</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>{t('terminology.colDisplay')}</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>{t('terminology.colSystem')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {searchResults.map((r, idx) => (
                      <TableRow key={idx}>
                        <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>{r.code || ''}</TableCell>
                        <TableCell sx={{ fontSize: '0.8rem' }}>{r.display || ''}</TableCell>
                        <TableCell sx={{ fontSize: '0.75rem', fontFamily: 'monospace' }}>{r.system || ''}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Stack>
        </AccordionDetails>
      </Accordion>
    </Stack>
  )
}
