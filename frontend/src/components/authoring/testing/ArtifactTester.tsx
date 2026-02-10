import { useState } from 'react'
import {
  Box, Stack, Typography, TextField, Alert, CircularProgress,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Chip, Accordion, AccordionSummary, AccordionDetails,
} from '@mui/material'
import { ExpandMore as ExpandMoreIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import { useTestArtifact } from '../../../hooks/useArtifactTesting'
import type { ArtifactTestResult } from '../../../types/authoring'

interface ArtifactTesterProps {
  artifactId: number
}

export default function ArtifactTester({ artifactId }: ArtifactTesterProps) {
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
      <Typography variant="h6" sx={{ mb: 2 }}>Test Artifact</Typography>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Execute the generated CQL against patient data from a FHIR server to verify your artifact logic.
      </Typography>

      <Stack spacing={2} sx={{ mb: 3 }}>
        <TextField
          label="FHIR Server URL"
          value={fhirServerUrl}
          onChange={(e) => setFhirServerUrl(e.target.value)}
          size="small"
          fullWidth
          placeholder="e.g., http://localhost:8080/fhir"
        />
        <TextField
          label="Patient IDs"
          value={patientIdsInput}
          onChange={(e) => setPatientIdsInput(e.target.value)}
          size="small"
          fullWidth
          multiline
          minRows={2}
          placeholder="Enter patient IDs separated by commas or new lines"
          helperText="e.g., patient-1, patient-2, patient-3"
        />
        <Box>
          <GradientButton
            onClick={handleTest}
            disabled={testMutation.isPending || !patientIdsInput.trim() || !fhirServerUrl.trim()}
          >
            {testMutation.isPending ? 'Testing...' : 'Run Test'}
          </GradientButton>
          {testMutation.isPending && <CircularProgress size={20} sx={{ ml: 1, verticalAlign: 'middle' }} />}
        </Box>
      </Stack>

      {testMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Test failed: {(testMutation.error as Error)?.message || 'Unknown error'}
        </Alert>
      )}

      {result && (
        <>
          <Alert
            severity={result.successCount === result.totalPatients ? 'success' : 'warning'}
            sx={{ mb: 2 }}
          >
            Tested {result.totalPatients} patient(s): {result.successCount} succeeded,{' '}
            {result.totalPatients - result.successCount} failed.
          </Alert>

          <TableContainer component={Paper} variant="outlined" sx={{ mb: 2 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Patient ID</TableCell>
                  <TableCell>Inclusion</TableCell>
                  <TableCell>Exclusion</TableCell>
                  <TableCell>In Population</TableCell>
                  <TableCell>Recommendation</TableCell>
                  <TableCell>Status</TableCell>
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
                        label={pr.success ? 'OK' : 'Error'}
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
                <Typography variant="subtitle2">Detailed Expression Results</Typography>
              </AccordionSummary>
              <AccordionDetails>
                {result.patientResults
                  .filter((pr) => pr.expressions)
                  .map((pr) => (
                    <Box key={pr.patientId} sx={{ mb: 2 }}>
                      <Typography variant="caption" sx={{ fontWeight: 600 }}>
                        Patient: {pr.patientId}
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
                <Typography variant="subtitle2">Generated CQL</Typography>
              </AccordionSummary>
              <AccordionDetails>
                <Box
                  sx={{
                    p: 1.5,
                    borderRadius: 1,
                    backgroundColor: (theme) => theme.palette.mode === 'dark' ? '#1e1e1e' : '#f5f5f5',
                    fontFamily: '"Fira Code", "Consolas", monospace',
                    fontSize: '0.8rem',
                    whiteSpace: 'pre-wrap',
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
