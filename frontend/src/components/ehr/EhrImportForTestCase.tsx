import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Stack,
  Alert,
  CircularProgress,
  Box,
  Stepper,
  Step,
  StepLabel,
  TextField,
  MenuItem,
  Chip,
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'
import { ehrApi } from '../../api'
import type { PatientSearchResult } from '../../types'
import { STALE_5M, STALE_30S } from '../../constants/queryConstants'
import GradientButton from '../common/GradientButton'

interface EhrImportForTestCaseProps {
  open: boolean
  measureId?: number
  onClose: () => void
  onImported: (bundleJson: string) => void
}

export default function EhrImportForTestCase({
  open,
  measureId,
  onClose,
  onImported,
}: EhrImportForTestCaseProps) {
  const { t } = useTranslation('fhir')
  const [step, setStep] = useState(0)
  const [connectionId, setConnectionId] = useState<number | ''>('')
  const [nationalId, setNationalId] = useState('')
  const [mrn, setMrn] = useState('')
  const [family, setFamily] = useState('')
  const [given, setGiven] = useState('')
  const [searchTriggered, setSearchTriggered] = useState(false)
  const [selectedPatient, setSelectedPatient] = useState<PatientSearchResult | null>(null)

  const { data: connections = [] } = useQuery({
    queryKey: ['ehr-connections'],
    queryFn: () => ehrApi.getConnections(),
    enabled: open,
    staleTime: STALE_5M,
  })

  const hasSearchParams = !!(nationalId || mrn || family || given)

  const { data: searchResults = [], isFetching: searching } = useQuery({
    queryKey: ['ehr-patient-search-tc', connectionId, nationalId, mrn, family, given],
    queryFn: () =>
      ehrApi.searchPatients(connectionId as number, {
        nationalId: nationalId || undefined,
        mrn: mrn || undefined,
        family: family || undefined,
        given: given || undefined,
      }),
    enabled: searchTriggered && !!connectionId && hasSearchParams,
    staleTime: STALE_30S,
  })

  const { data: preview, isLoading: loadingPreview } = useQuery({
    queryKey: ['ehr-patient-preview-tc', connectionId, selectedPatient?.id],
    queryFn: () => ehrApi.getPatientPreview(connectionId as number, selectedPatient!.id),
    enabled: step === 2 && !!connectionId && !!selectedPatient,
    staleTime: STALE_5M,
  })

  const handleImport = async () => {
    try {
      await ehrApi.importPatient(
        connectionId as number,
        selectedPatient!.id,
        measureId
      )
      // Build a bundle from the patient data for the editor
      const bundle = {
        resourceType: 'Bundle',
        type: 'collection',
        entry: [
          {
            resource: {
              resourceType: 'Patient',
              id: selectedPatient!.id,
              name: [{ text: selectedPatient!.name }],
              gender: selectedPatient!.gender || undefined,
              birthDate: selectedPatient!.birthDate || undefined,
            },
          },
        ],
      }
      onImported(JSON.stringify(bundle, null, 2))
    } catch {
      // Error will be visible through the UI
    }
  }

  const handleSearch = () => {
    if (connectionId && hasSearchParams) {
      setSearchTriggered(true)
    }
  }

  const handleSelectPatient = (patient: PatientSearchResult) => {
    setSelectedPatient(patient)
    setStep(2)
  }

  const steps = [t('ehr.selectConnection'), t('ehr.patientSearch'), t('ehr.importPreview')]

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{t('ehr.importFromEhr')}</DialogTitle>
      <DialogContent>
        <Stepper activeStep={step} sx={{ mb: 3 }}>
          {steps.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>

        {/* Step 0: Select connection + search params */}
        {step === 0 && (
          <Stack spacing={2}>
            <TextField
              select
              label={t('ehr.selectConnection')}
              value={connectionId}
              onChange={(e) => setConnectionId(Number(e.target.value))}
              size="small"
              fullWidth
            >
              {connections
                .filter((c) => c.active !== false)
                .map((c) => (
                  <MenuItem key={c.id} value={c.id}>
                    {c.name}
                    {c.status === 'success' && (
                      <Chip
                        label={t('ehr.status.success')}
                        size="small"
                        color="success"
                        sx={{ ml: 1 }}
                      />
                    )}
                  </MenuItem>
                ))}
            </TextField>

            {connectionId && (
              <Button variant="contained" onClick={() => setStep(1)} size="small">
                {t('ehr.patientSearch')}
              </Button>
            )}
          </Stack>
        )}

        {/* Step 1: Search patients */}
        {step === 1 && (
          <Stack spacing={2}>
            <Stack direction="row" spacing={1}>
              <TextField
                label={t('ehr.nationalId')}
                value={nationalId}
                onChange={(e) => { setNationalId(e.target.value); setSearchTriggered(false) }}
                size="small"
                sx={{ flex: 1 }}
              />
              <TextField
                label={t('ehr.mrn')}
                value={mrn}
                onChange={(e) => { setMrn(e.target.value); setSearchTriggered(false) }}
                size="small"
                sx={{ flex: 1 }}
              />
            </Stack>
            <Stack direction="row" spacing={1}>
              <TextField
                label={t('ehr.familyName')}
                value={family}
                onChange={(e) => { setFamily(e.target.value); setSearchTriggered(false) }}
                size="small"
                sx={{ flex: 1 }}
              />
              <TextField
                label={t('ehr.givenName')}
                value={given}
                onChange={(e) => { setGiven(e.target.value); setSearchTriggered(false) }}
                size="small"
                sx={{ flex: 1 }}
              />
            </Stack>
            <Box>
              <GradientButton
                startIcon={<SearchIcon />}
                onClick={handleSearch}
                disabled={!hasSearchParams || searching}
              >
                {t('ehr.search')}
              </GradientButton>
            </Box>

            {searchTriggered && searchResults.length === 0 && !searching && (
              <Typography
                variant="body2"
                sx={{
                  color: "text.secondary",
                  textAlign: 'center',
                  py: 2
                }}>
                {t('ehr.noResults')}
              </Typography>
            )}

            {searchResults.length > 0 && (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600 }}>{t('ehr.patientName')}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{t('ehr.identifier')}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{t('ehr.birthDate')}</TableCell>
                    <TableCell align="right" />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {searchResults.map((p) => (
                    <TableRow key={p.id}>
                      <TableCell>{p.name}</TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                          {p.identifier || p.id}
                        </Typography>
                      </TableCell>
                      <TableCell>{p.birthDate || '--'}</TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={() => handleSelectPatient(p)}
                        >
                          {t('ehr.preview')}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        )}

        {/* Step 2: Preview and import */}
        {step === 2 && selectedPatient && (
          <Stack spacing={2}>
            <Box>
              <Typography variant="subtitle2" sx={{
                color: "text.secondary"
              }}>
                {t('ehr.patientName')}
              </Typography>
              <Typography variant="body1" sx={{ fontWeight: 500 }}>
                {selectedPatient.name}
              </Typography>
            </Box>

            {loadingPreview ? (
              <Box sx={{ textAlign: 'center', py: 3 }}>
                <CircularProgress size={24} />
              </Box>
            ) : preview ? (
              <>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600 }}>{t('ehr.resourceType')}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 600 }}>{t('ehr.count')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {Object.entries(preview.resourceCounts).map(([type, count]) => (
                      <TableRow key={type}>
                        <TableCell>{type}</TableCell>
                        <TableCell align="right">{count}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {t('ehr.totalResources')}: {preview.totalResources}
                </Typography>
              </>
            ) : (
              <Alert severity="info">{t('ehr.noResults')}</Alert>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        {step > 0 && (
          <Button onClick={() => setStep(step - 1)}>
            {t('ehr.back')}
          </Button>
        )}
        <Button onClick={onClose}>{t('ehr.cancel')}</Button>
        {step === 2 && selectedPatient && (
          <Button variant="contained" onClick={handleImport}>
            {t('ehr.importPatient')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
