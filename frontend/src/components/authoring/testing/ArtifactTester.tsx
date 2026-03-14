import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Box, Stack, Typography, TextField, Alert, CircularProgress,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Chip, Accordion, AccordionSummary, AccordionDetails,
} from '@mui/material'
import { ExpandMore as ExpandMoreIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import { useTestArtifact } from '../../../hooks/useArtifactTesting'
import type { ArtifactTestResult } from '../../../types/authoring'
import { codeBlockSx } from '../../../constants/authoringConstants'

interface ArtifactTesterProps {
  artifactId: number
}

export default function ArtifactTester({ artifactId }: ArtifactTesterProps) {
  const { t } = useTranslation('authoring')
  const [patientIdsInput, setPatientIdsInput] = useState('')
  const [fhirServerUrl, setFhirServerUrl] = useState('')
  const [result, setResult] = useState<ArtifactTestResult | null>(null)

  const testMutation = useTestArtifact()

  const handleTest = () => {
    const patientIds = patientIdsInput
      .split(/[,\n]/)
      .map((id) => id.trim())
      .filter(Boolean)

    if (patientIds.length === 0) return

    testMutation.mutate(
      { id: artifactId, patientIds, fhirServerUrl },
      { onSuccess: (data) => setResult(data) }
    )
  }

  return (
    <Box>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('testing.title')}</Typography>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('testing.description')}
      </Typography>

      <Stack spacing={2} sx={{ mb: 3 }}>
        <TextField
          label={t('testing.fhirServerLabel')}
          value={fhirServerUrl}
          onChange={(e) => setFhirServerUrl(e.target.value)}
          size="small"
          fullWidth
          placeholder={t('testing.fhirServerPlaceholder')}
          helperText={t('testing.fhirServerHelper')}
        />
        <TextField
          label={t('testing.patientIdsLabel')}
          value={patientIdsInput}
          onChange={(e) => setPatientIdsInput(e.target.value)}
          size="small"
          fullWidth
          multiline
          minRows={2}
          placeholder={t('testing.patientIdsPlaceholder')}
          helperText={t('testing.patientIdsHelper')}
        />
        <Box>
          <GradientButton
            onClick={handleTest}
            disabled={testMutation.isPending || !patientIdsInput.trim() || !fhirServerUrl.trim()}
          >
            {testMutation.isPending ? t('testing.testing') : t('testing.runTest')}
          </GradientButton>
          {testMutation.isPending && <CircularProgress size={20} sx={{ ml: 1, verticalAlign: 'middle' }} />}
        </Box>
      </Stack>

      {testMutation.isError && (
        <Alert severity="error" onClose={() => testMutation.reset()} sx={{ mb: 2 }}>
          <Typography variant="subtitle2">{t('testing.testFailed')}</Typography>
          <Typography variant="body2" sx={{ mb: 0.5 }}>
            {(testMutation.error as Error)?.message || 'Unknown error'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {t('testing.testFailedHint')}
          </Typography>
        </Alert>
      )}

      {result && (
        <>
          <Alert
            severity={result.successCount === result.totalPatients ? 'success' : 'warning'}
            sx={{ mb: 2 }}
          >
            {t('testing.resultSummary', { total: result.totalPatients, success: result.successCount, failed: result.totalPatients - result.successCount })}
          </Alert>

          <TableContainer component={Paper} variant="outlined" sx={{ mb: 2 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t('testing.colPatientId')}</TableCell>
                  <TableCell>{t('testing.colInclusion')}</TableCell>
                  <TableCell>{t('testing.colExclusion')}</TableCell>
                  <TableCell>{t('testing.colInPopulation')}</TableCell>
                  <TableCell>{t('testing.colRecommendation')}</TableCell>
                  <TableCell>{t('testing.colStatus')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {result.patientResults.map((pr) => (
                  <TableRow key={pr.patientId}>
                    <TableCell sx={{ fontWeight: 500 }}>{pr.patientId}</TableCell>
                    <TableCell>
                      <BooleanChip value={pr.meetsInclusion} />
                    </TableCell>
                    <TableCell>
                      <BooleanChip value={pr.meetsExclusion} />
                    </TableCell>
                    <TableCell>
                      <BooleanChip value={pr.inPopulation} />
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ maxWidth: 200, display: 'block' }} noWrap>
                        {pr.recommendation || '-'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={pr.success ? t('testing.ok') : t('testing.error')}
                        color={pr.success ? 'success' : 'error'}
                        size="small"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          {/* Detailed expression results */}
          {result.patientResults.some((pr) => pr.expressions) && (
            <Accordion variant="outlined">
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="subtitle2">{t('testing.detailedResults')}</Typography>
              </AccordionSummary>
              <AccordionDetails>
                {result.patientResults
                  .filter((pr) => pr.expressions)
                  .map((pr) => (
                    <Box key={pr.patientId} sx={{ mb: 2 }}>
                      <Typography variant="caption" sx={{ fontWeight: 600 }}>
                        {t('testing.patient', { id: pr.patientId })}
                      </Typography>
                      <Table size="small">
                        <TableBody>
                          {Object.entries(pr.expressions!).map(([name, val]) => (
                            <TableRow key={name}>
                              <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                                {name}
                              </TableCell>
                              <TableCell>
                                <Chip label={val.type} size="small" variant="outlined" sx={{ mr: 1 }} />
                                {val.value}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </Box>
                  ))}
              </AccordionDetails>
            </Accordion>
          )}

          {/* Generated CQL preview */}
          {result.cql && (
            <Accordion variant="outlined" sx={{ mt: 1 }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="subtitle2">{t('testing.generatedCql')}</Typography>
              </AccordionSummary>
              <AccordionDetails>
                <Box
                  sx={{
                    ...codeBlockSx,
                    fontSize: '0.8rem',
                    maxHeight: 400,
                    overflow: 'auto',
                  }}
                >
                  {result.cql}
                </Box>
              </AccordionDetails>
            </Accordion>
          )}
        </>
      )}
    </Box>
  )
}

function BooleanChip({ value }: { value?: string }) {
  if (!value) return <Typography variant="caption" color="text.secondary">-</Typography>
  const isTrue = value === 'true'
  return <Chip label={value} size="small" color={isTrue ? 'success' : 'default'} variant="outlined" />
}
