import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  Box,
  Typography,
  TextField,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Button,
  MenuItem,
  Chip,
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'
import { ehrApi } from '../../api'
import type { EhrConnection, PatientSearchResult } from '../../types'
import { STALE_30S } from '../../constants/queryConstants'
import GradientButton from '../common/GradientButton'

interface PatientSearchPanelProps {
  connections: EhrConnection[]
  onSelectPatient: (connectionId: number, patient: PatientSearchResult) => void
}

export default function PatientSearchPanel({ connections, onSelectPatient }: PatientSearchPanelProps) {
  const { t } = useTranslation('fhir')
  const [connectionId, setConnectionId] = useState<number | ''>('')
  const [nationalId, setNationalId] = useState('')
  const [mrn, setMrn] = useState('')
  const [family, setFamily] = useState('')
  const [given, setGiven] = useState('')
  const [searchEnabled, setSearchEnabled] = useState(false)

  const hasSearchParams = !!(nationalId || mrn || family || given)

  const { data: results = [], isFetching } = useQuery({
    queryKey: ['ehr-patient-search', connectionId, nationalId, mrn, family, given],
    queryFn: () =>
      ehrApi.searchPatients(connectionId as number, {
        nationalId: nationalId || undefined,
        mrn: mrn || undefined,
        family: family || undefined,
        given: given || undefined,
      }),
    enabled: searchEnabled && !!connectionId && hasSearchParams,
    staleTime: STALE_30S,
  })

  const handleSearch = () => {
    if (connectionId && hasSearchParams) {
      setSearchEnabled(true)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSearch()
  }

  return (
    <Box>
      <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
        {t('ehr.patientSearch')}
      </Typography>

      <Stack spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          label={t('ehr.selectConnection')}
          value={connectionId}
          onChange={(e) => {
            setConnectionId(Number(e.target.value))
            setSearchEnabled(false)
          }}
          size="small"
          fullWidth
        >
          {connections.map((c) => (
            <MenuItem key={c.id} value={c.id}>
              {c.name}
              {c.status === 'success' && (
                <Chip label={t('ehr.status.success')} size="small" color="success" sx={{ ml: 1 }} />
              )}
            </MenuItem>
          ))}
        </TextField>

        <Stack direction="row" spacing={1}>
          <TextField
            label={t('ehr.nationalId')}
            value={nationalId}
            onChange={(e) => { setNationalId(e.target.value); setSearchEnabled(false) }}
            onKeyDown={handleKeyDown}
            size="small"
            sx={{ flex: 1 }}
          />
          <TextField
            label={t('ehr.mrn')}
            value={mrn}
            onChange={(e) => { setMrn(e.target.value); setSearchEnabled(false) }}
            onKeyDown={handleKeyDown}
            size="small"
            sx={{ flex: 1 }}
          />
        </Stack>

        <Stack direction="row" spacing={1}>
          <TextField
            label={t('ehr.familyName')}
            value={family}
            onChange={(e) => { setFamily(e.target.value); setSearchEnabled(false) }}
            onKeyDown={handleKeyDown}
            size="small"
            sx={{ flex: 1 }}
          />
          <TextField
            label={t('ehr.givenName')}
            value={given}
            onChange={(e) => { setGiven(e.target.value); setSearchEnabled(false) }}
            onKeyDown={handleKeyDown}
            size="small"
            sx={{ flex: 1 }}
          />
        </Stack>

        <Box>
          <GradientButton
            startIcon={<SearchIcon />}
            onClick={handleSearch}
            disabled={!connectionId || !hasSearchParams || isFetching}
          >
            {t('ehr.search')}
          </GradientButton>
        </Box>
      </Stack>

      {searchEnabled && results.length === 0 && !isFetching && (
        <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
          {t('ehr.noResults')}
        </Typography>
      )}

      {results.length > 0 && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>{t('ehr.patientName')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('ehr.identifier')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('ehr.birthDate')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{t('ehr.gender')}</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {results.map((patient) => (
                <TableRow key={patient.id}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {patient.name}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                      {patient.identifier || patient.id}
                    </Typography>
                  </TableCell>
                  <TableCell>{patient.birthDate || '--'}</TableCell>
                  <TableCell>{patient.gender || '--'}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => onSelectPatient(connectionId as number, patient)}
                    >
                      {t('ehr.preview')}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  )
}
